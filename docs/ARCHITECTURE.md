# ARCHITECTURE — War of the Rift

Версия документа: 1 · Дата: 2026-10-24 · Платформа: Minecraft 1.20.1, Forge 47.x, Java 17, official mappings.

Статус: **проектная архитектура**. Ни один класс ещё не реализован (см. `IMPLEMENTATION_STATUS.md`). Имена ниже — окончательные; менять их можно только вместе с этим документом и `CLAUDE.md`.

---

## 1. Три слоя и главное разделение

```
┌──────────────────────────────────────────────────────────────────────┐
│ КЛИЕНТ (Dist.CLIENT)                                                 │
│ WarJournalScreen · ClientWarState · ClientModEvents/ClientForgeEvents│
│   ▲ WarSnapshot (S2C)                         │ запрос (C2S)         │
└───┼───────────────────────────────────────────┼──────────────────────┘
    │            NetworkHandler (SimpleChannel)  ▼
┌───┴──────────────────────────────────────────────────────────────────┐
│ СЕРВЕР                                                               │
│                                                                      │
│  СЛОЙ 3. РЕАЛЬНАЯ БОЕВАЯ СИМУЛЯЦИЯ (живые Entity, загруженные чанки) │
│    FactionCombatSystem · entity/ai · SettlementGuardEntity           │
│    RiftCommanderEntity · учёт в WarRuntime                           │
│            ▲ materialize                │ reconcile                  │
│  ──────────┼─────────────────────────────┼──── граница: battle/ ──── │
│            │  BattleMaterializer         ▼  BattleReconciler          │
│  СЛОЙ 2. СТРАТЕГИЧЕСКИЙ РАСЧЁТ (только числа, без Level/чанков/спавна)│
│    WarSimulationManager · StrategicBattleResolver · WarRandom        │
│    InvasionManager (стадии/волны/интенсивность)                      │
│            │ читает/пишет                                            │
│  СЛОЙ 1. СОХРАНЯЕМАЯ МОДЕЛЬ                                          │
│    WarSavedData (Overworld) · data/model/* · WarEventLog             │
│    реестры-фасады: PortalRegistry · SettlementManager                │
│                                                                      │
│  Сквозное: WarOfTheRiftConfig · FactionResolver · WarRuntime         │
│            event/WarModBusEvents · event/WarForgeBusEvents           │
│            WarOfTheRiftCommands                                      │
└──────────────────────────────────────────────────────────────────────┘
```

Правило зависимостей: слой может обращаться только вниз (слой 3 → слой 2 через `battle/`, слой 2 → слой 1). Слой 1 и слой 2 не знают о слое 3. Клиент не обращается к серверным классам; сервер не загружает клиентские.

### 1.1 Реальная боевая симуляция ≠ стратегический расчёт

| | Реальная симуляция | Стратегический расчёт |
|---|---|---|
| Где | загруженные чанки рядом с игроком | любые фронты, в т. ч. выгруженные |
| Что исполняет | настоящие `Entity`, ИИ мобов, урон | числа в `FrontRecord`/`ForceRecord` |
| Кто управляет | ваниль + `FactionCombatSystem` + цели ИИ | `WarSimulationManager` по расписанию |
| Что запрещено | бесконечный спавн, превышение лимитов | любые вызовы `Level`, `ChunkSource`, спавн, изменение блоков |
| Результат | события смерти/выживания | потери, захват, падение, события журнала |

Эмуляция далёкого боя массовым спавном мобов **запрещена**. Далёкий бой — только арифметика.

### 1.2 Авторитет фронта

`FrontRecord.authority ∈ {STRATEGIC, REAL}`; в любой момент ровно один.

- `STRATEGIC → REAL`: игрок (в любом измерении) ближе `limits.materializeRadius` к центру фронта **и** чанки площадки загружены (`level.hasChunkAt`/`isLoaded`, без форсирования загрузки). Материализуется не больше доли сил фронта и в пределах бюджетов (`limits.*`). Каждая группа получает `batchId`.
- `REAL → STRATEGIC`: нет игроков ближе `limits.dematerializeRadius` (> materialize, гистерезис) в течение `limits.dematerializeGraceTicks`, либо бой завершён. `BattleReconciler`: потери (накоплены по `LivingDeathEvent`), выжившие (по счётчикам `WarRuntime`) → обратно в `FrontRecord`; `batchId` закрывается; оставшиеся реальные сущности удаляются через `discard()` только пока они в загруженной области и вдали от игроков.
- Инвариант: `материализовано + остаток_стратегии = было_до − потери`; проверяется юнит-тестом и GameTest.
- Сущности закрытого `batchId`, снова появившиеся при загрузке чанка (`EntityJoinLevelEvent`), «поглощаются» (удаляются без повторного учёта), потому что уже учтены стратегически (A20).

## 2. Окончательные имена классов и ответственность

### 2.1 `WarOfTheRiftMod` (корень)
Точка входа `@Mod("waroftherift")`. Получает mod event bus (`FMLJavaModLoadingContext.get().getModEventBus()`), регистрирует `DeferredRegister` только реально существующих объектов, `ModLoadingContext.get().registerConfig(...)`, один раз регистрирует `WarModBusEvents`, `WarForgeBusEvents`, и (через `DistExecutor`/`@Mod.EventBusSubscriber(value = Dist.CLIENT)`) клиентские обработчики. Никакой игровой логики и статического состояния.

### 2.2 `FactionResolver` (+ `Faction`)
- Что: определяет фракцию `LivingEntity`/`EntityType` → `Faction` (`SETTLEMENTS`, `ILLAGERS`, `NETHER_LEGION`) или `NONE`.
- Как: теги типов сущностей `data/waroftherift/tags/entity_types/faction/{settlements,illagers,nether_legion}.json` (расширяемы датапаками и другими модами), кэш `EntityType → Faction`, сбрасывается при перезагрузке данных (`TagsUpdatedEvent`) и при остановке сервера.
- Исключения (A6): голем, созданный игроком, и приручённые/принадлежащие игроку существа по умолчанию не втягиваются в войну; настраивается конфигом. Игрок фракцией не является.
- Не делает: не хранит состояние войны, не принимает решений о боях.
- Дефолтный состав по тегам: см. `CLAUDE.md` §1; `minecraft:raiders` используется как источник для ILLAGERS после сверки с 1.20.1 (A21).

### 2.3 `WarSavedData` (+ `data/model/*`)
- Что: единый `SavedData`, привязанный к Overworld: `server.overworld().getDataStorage().computeIfAbsent(WarSavedData::load, WarSavedData::new, "waroftherift_war")` (сигнатура 1.20.1, A12).
- Содержит (только примитивы/строки/UUID/ResourceLocation-строки/long-позиции): `WarState` (стадия, интенсивность, `lastSimGameTime`, счётчики, сид), коллекции `PortalRecord`, `SettlementRecord`, `FrontRecord`, `ForceRecord`, кольцевой буфер `WarEventRecord`, закрытые `batchId`.
- Версионирование: поле `dataVersion`, миграции при загрузке; неизвестные поля сохраняются/игнорируются безопасно.
- Лимиты: размеры коллекций из конфига; обрезка старых записей журнала; `setDirty()` только при реальном изменении.
- Доступ: только через фасады (`PortalRegistry`, `SettlementManager`, `WarEventLog`, `WarSimulationManager`, `InvasionManager`), не напрямую из UI и событий.
- Запрещено: ссылки на `Level`, `Entity`, `Player`; хранение `Component` с живым состоянием.

### 2.4 `PortalRegistry`
- Что: учёт известных порталов: `PortalRecord(id, dimension, bounds/anchor, pairedPortalId, openedAtGameTime, openedBy(UUID, необязательно), state)`.
- Источники сведений (A17): `BlockEvent.PortalSpawnEvent` (создание), `EntityTravelToDimensionEvent` (пересечение — подтверждение/обнаружение «чужих» порталов), ограниченный локальный запрос POI вокруг игрока (`PoiManager.getInRange`, только загруженные секции) при пересечении. Глобального поиска блоков нет.
- Дедупликация по якорю + измерению; обновление, а не дубль.
- Сообщает `InvasionManager` о новом/закрытом портале; пишет в `WarEventLog`.
- Не делает: не создаёт войска, не считает бои.

### 2.5 `SettlementManager` (+ `SettlementScanner`)
- Что: реестр поселений: `SettlementRecord(id, dimension, center, radius, population, defense, garrison, status, controlFaction, lastSeenGameTime)`.
- Обнаружение (A7): не глобальный поиск. `SettlementScanner` срабатывает на уже загруженных областях: при входе жителя в мир, при взаимодействии игрока рядом с деревней, периодически вокруг игроков с бюджетом на тик. Определение — по POI (колокол/кровати/рабочие места) и/или `StructureManager.getStructureWithPieceAt(pos, StructureTags.VILLAGE)`.
- Население/оборона/гарнизон: считаются по счётчикам (`WarRuntime`) и последнему известному значению; в выгруженной области значения только стратегические.
- Статусы: `STABLE`, `THREATENED`, `UNDER_ATTACK`, `FALLEN`, `ABANDONED`.
- Не делает: не ломает постройки, не удаляет поселение из мира.

### 2.6 `InvasionManager`
- Что: стадии вторжения (enum `InvasionStage`: `DORMANT → PROBING → FOOTHOLD → ASSAULT → OCCUPATION`), интенсивность, плацдармы (создание/рост/закрытие как данные), расписание и состав волн (`ForceRecord`), условие старта (A19).
- Адаптация: интенсивность — функция от числа активных порталов, времени, давления и потерь сторон, множителя `invasion.intensityMultiplier`.
- Вход: события `PortalRegistry`, шаги `WarSimulationManager`. Выход: изменения `WarSavedData` + запросы материализации в `battle/`.
- Лимиты: макс. плацдармов на измерение, макс. размер волны, интервалы волн — в конфиге. Никаких случайных решений на каждом тике — только в шаге по расписанию, через `WarRandom`.

### 2.7 `WarSimulationManager` (+ `StrategicBattleResolver`, `WarRandom`)
- Что: стратегическая модель. Шаг симуляции раз в `simulation.stepIntervalTicks` (по `overworld.getGameTime()`), с ограничением числа «догоняющих» шагов `simulation.maxCatchUpSteps` (A18).
- Что считает: силы и потери на фронтах (`StrategicBattleResolver` — чистая функция: входные числа → результат), захват/потеря территории, падение поселений, беженцы (данные), рост плацдармов.
- Детерминизм: `WarRandom` на `SplittableRandom`-подобной схеме с сидом `f(worldSeed, frontId, stepIndex)`; одинаковые входы → одинаковые результаты.
- Запрещено внутри: `Level`, `ChunkSource`, `Entity`, спавн, блоки. Модуль не импортирует `net.minecraft.world.level.*` и т. п. там, где можно обойтись числами (проверка при ревью).
- Фронты в состоянии `REAL` стратегически **не** пересчитываются (авторитет у реальной боёвки).

### 2.8 `FactionCombatSystem` (+ `FactionRelation`, `combat/ai`)
- Что: правила «кто кого считает целью». Матрица `Faction × Faction → FactionRelation` (`HOSTILE`, `NEUTRAL`, `OPPORTUNISTIC`); по умолчанию: `SETTLEMENTS`↔`NETHER_LEGION` — `HOSTILE`; `ILLAGERS`↔`NETHER_LEGION` — `OPPORTUNISTIC` (бой, если легион мешает рейду / в зоне интереса); `ILLAGERS`↔`SETTLEMENTS` — `HOSTILE` (ваниль); внутри фракции дружественного огня нет.
- Интеграция с ИИ (A4):
  - `Mob` с `GoalSelector` (голем, разорители, гаст, ифрит, магмовый куб, скелет-иссушитель, Settlement Guard, Rift Commander): добавление целей один раз на `EntityJoinLevelEvent` с защитой от дублей (флаг в `PersistentData`).
  - Мобы на `Brain` (пиглин, брют, хоглин, зоглин, житель): целей через `GoalSelector` нет; используется `LivingChangeTargetEvent` и запись `MemoryModuleType.ATTACK_TARGET`.
- Ограничения: радиус и частота поиска цели из конфига, ленивое кэширование «ближайшей цели» на моб, игроки не затрагиваются, если `combat.targetPlayers=false`.
- Не делает: не создаёт и не удаляет мобов, не меняет стратегические данные (только сообщает через `WarRuntime`/события смерти).

### 2.9 `BattleMaterializer` / `BattleReconciler` (граница слоёв)
См. §1.2. Материализатор создаёт мобов только в загруженных чанках, в рамках бюджетов `limits.*`, с флагом persistence по правилам A10; зомбификацию в Верхнем мире подавляет (A9). Реконсилер — единственное место, где реальные последствия возвращаются в стратегические данные.

### 2.10 `WarEventLog`
- Что: история событий (`WarEventRecord(gameTime, type, dimension, pos?, factionA?, factionB?, args, messageKey)`), кольцевой буфер в `WarSavedData`, размер `journal.maxEvents`.
- События: открытие портала, новая стадия, плацдарм, начало/конец боя, потери, захват/потеря территории, падение/возвращение поселения, прибытие беженцев.
- Текст не хранится в готовом виде: хранится `messageKey` + аргументы (локализация на клиенте, ru/en).
- API: `append`, `recent(n)`, `query(filter)`; уведомление игроков — отдельным механизмом с ограничением частоты (без спама чата).

### 2.11 `NetworkHandler`
- `SimpleChannel` (`NetworkRegistry.newSimpleChannel`), строка версии протокола, `clientAcceptedVersions`/`serverAcceptedVersions` совпадают.
- Пакеты (`network/packet`):
  - `RequestWarSnapshotC2S` — запрос (с типом вкладки/страницей).
  - `WarSnapshotS2C` — только чтение: `WarSnapshot` (DTO, ограниченный размер, пагинация журнала).
  - Опционально позже: уведомления о событиях (S2C), админ-команды остаются в Brigadier, не в пакетах.
- Правила: обработка в `enqueueWork`; валидация отправителя; ограничение частоты запросов на игрока; никаких клиентских записей в `WarSavedData`.

### 2.12 `WarJournalScreen` (+ `ClientWarState`, `ClientModEvents`, `ClientForgeEvents`)
- Экран журнала: вкладки «Обзор», «Фронты», «Поселения», «Порталы», «История», «Потери».
- Источник данных — только кэш `ClientWarState` из `WarSnapshotS2C`; экран ничего не изменяет на сервере.
- Открытие: клавиша (`RegisterKeyMappingsEvent`, регистрация в `ClientModEvents`), позже — предмет.
- Клиентские классы подгружаются только при `Dist.CLIENT`.

### 2.13 `WarOfTheRiftConfig` (Config)
- `ForgeConfigSpec`; игровые параметры — `ModConfig.Type.SERVER` (файл мира `serverconfig/waroftherift-server.toml`, синхронизируется; читать только после старта сервера — A13); клиентские (UI) — `CLIENT`.
- Предварительные ключи (значения — стартовые, уточняются на этапе баланса):

| Ключ | По умолчанию | Назначение |
|---|---|---|
| `general.enabled` | true | глобальный выключатель войны |
| `general.debugLogging` | false | подробные логи |
| `invasion.intensityMultiplier` | 1.0 (0.1–5.0) | множитель интенсивности |
| `invasion.autonomousProgression` | true | война идёт без участия игрока после старта |
| `invasion.naturalRiftsEnabled` | false | разрешить старт без портала игрока (A19) |
| `invasion.maxBeachheadsPerDimension` | 6 | лимит плацдармов |
| `invasion.maxWaveSize` | 12 | лимит состава волны |
| `simulation.stepIntervalTicks` | 1200 (≥200) | период стратегического шага |
| `simulation.maxCatchUpSteps` | 5 | ограничение догоняющих шагов |
| `simulation.maxFronts` | 32 | лимит фронтов |
| `limits.maxWarMobsGlobal` | 80 | глобальный лимит реальных боевых мобов |
| `limits.maxWarMobsPerPlayerArea` | 40 | лимит рядом с игроком |
| `limits.maxWarMobsPerFront` | 24 | лимит на фронт |
| `limits.spawnBudgetPerTick` | 2 | спавнов за тик |
| `limits.materializeRadius` | 96 | активация фронта |
| `limits.dematerializeRadius` | 128 | деактивация (гистерезис) |
| `limits.dematerializeGraceTicks` | 600 | задержка деактивации |
| `settlements.maxTracked` | 128 | лимит записей поселений |
| `settlements.scanBudgetPerTick` | 1 | бюджет сканера |
| `combat.targetSearchRadius` | 24 | радиус поиска цели |
| `combat.targetPlayers` | true | втягивать ли игрока в бой |
| `combat.includeOwnedMobs` | false | втягивать приручённых/игроковых (A6) |
| `journal.maxEvents` | 500 | размер кольцевого журнала |
| `network.minRequestIntervalTicks` | 20 | минимальный интервал запросов журнала от одного игрока |
| `consequences.allowBlockDamage` | false | любые разрушения блоков |

Ограничение: при `general.enabled=false` все обработчики быстро выходят, состояние не удаляется.

### 2.14 `WarOfTheRiftCommands`
Brigadier через `RegisterCommandsEvent`, корень `/waroftherift` (псевдоним `/wotr`), уровень прав 2. Подкоманды: `status`, `faction <entity>`, `intensity get|set`, `portals`, `settlements`, `fronts`, `log [n]`, `simulate <steps>`, `materialize <front>`, `debug on|off`, `reset confirm`. Команды вызывают фасады менеджеров и не содержат игровой логики.

### 2.15 `WarRuntime`
Временное состояние сервера: счётчики живых боевых мобов (по событиям, не сканированием), очереди материализации, кэши `FactionResolver`, rate-limit запросов журнала. Создаётся на `ServerStartingEvent`, очищается на `ServerStoppedEvent` (важно для одиночной игры: сервер пересоздаётся при каждом входе в мир). Не сохраняется.

### 2.16 `WarModBusEvents` / `WarForgeBusEvents`
Единственные точки подписки (по одной на шину), чтобы не было дублирования:
- mod bus: `FMLCommonSetupEvent` (сеть), `EntityAttributeCreationEvent`, регистрация параметров.
- Forge bus: `ServerStartingEvent`/`ServerStoppedEvent`, `TickEvent.ServerTickEvent` (одна диспетчеризация к менеджерам с интервалами), `EntityJoinLevelEvent`, `LivingDeathEvent`, `LivingChangeTargetEvent`, `BlockEvent.PortalSpawnEvent`, `EntityTravelToDimensionEvent`, `RegisterCommandsEvent`, `TagsUpdatedEvent`.
Клиентские аналоги — `ClientModEvents`, `ClientForgeEvents` (только `Dist.CLIENT`).

### 2.17 Сущности (`entity/`)
- `SettlementGuardEntity` — защитник поселений (патруль в радиусе поселения, защита жителей, реакция на плацдарм). Фракция `SETTLEMENTS`.
- `RiftCommanderEntity` — командир адских сил (усиление ближайших бойцов легиона, привязка к плацдарму/фронту, сигнал сбора). Фракция `NETHER_LEGION`.
- Базовые классы выбираются на этапе 8 после сверки с 1.20.1; рендер — на ванильных гуманоидных моделях с реально существующими текстурами (A15).

## 3. Жизненный цикл и тик

```
ServerStartingEvent   → создать WarRuntime; загрузить/создать WarSavedData; проверить версии данных
ServerTickEvent(END)  → диспетчер (один обработчик):
    каждые 20 тиков:   бюджетное обслуживание (SettlementScanner, проверка радиусов фронтов)
    каждые N тиков:    WarSimulationManager.step()  (интервал + ограничение догонки)
    каждый тик:        только очередь материализации (бюджет limits.spawnBudgetPerTick)
EntityJoinLevelEvent  → FactionResolver + регистрация цели ИИ (один раз) + учёт в WarRuntime
LivingDeathEvent      → потери в WarRuntime → FrontRecord (через BattleReconciler)
ServerStoppingEvent   → flush (setDirty), закрыть сетевые очереди
ServerStoppedEvent    → очистить WarRuntime и кэши
```

Бюджеты производительности (цели, уточнить измерениями на этапе 14): стратегический шаг — единицы мс; нет полного обхода сущностей; все обходы ограничены списками записей (≤ лимитов конфига).

## 4. Сохранение данных

- Хранилище: NBT внутри `world/data/waroftherift_war.dat`.
- Структура верхнего уровня: `dataVersion`, `state`, `portals[]`, `settlements[]`, `fronts[]`, `forces[]`, `events[]` (кольцо), `closedBatches[]`.
- Идентификаторы: `UUID` (или long) для записей; измерения — строка `ResourceLocation`; позиции — `BlockPos.asLong`.
- Переживает: выход из мира, смерть игрока, перезапуск сервера. Одна и та же запись на сервере и в одиночной игре.
- Реальные мобы, сохранённые вместе с миром, при загрузке опознаются по метке `waroftherift:front_id`/`batch_id` в `PersistentData`.

## 5. Сеть

Канал `waroftherift:main`, протокол `"1"`. Все данные для UI — снимок-DTO, формируется на сервере `WarSnapshotFactory` из `WarSavedData`; клиент хранит его только в `ClientWarState`. Размер пакета ограничен (пагинация журнала, максимальное число записей на страницу). Запросов не чаще, чем раз в `network.minRequestIntervalTicks` (конфиг, по умолчанию 20).

## 6. Dist-безопасность

- Любые импорты `net.minecraft.client.*` — только внутри пакета `client`.
- Регистрация клиентских обработчиков — `@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = ...)`/`DistExecutor`, не из общего кода.
- Общий код не вызывает `Minecraft.getInstance()`.
- Проверка: `./gradlew runServer` (G2).

## 7. Принципы тестируемости

- Чистое ядро (`simulation/StrategicBattleResolver`, `WarRandom`, формулы интенсивности, `data/model` сериализация без `Level`) — JUnit 5 без запуска игры (`testImplementation` — единственная допустимая сторонняя зависимость).
- Игровые системы — GameTest (`runGameTestServer`) и ручные сценарии, записанные в `IMPLEMENTATION_STATUS.md`.

## 8. Расширяемость

Состав фракций — теги (датапаки). Отношения — таблица в `FactionCombatSystem`, данные по умолчанию в коде + возможное переопределение конфигом на этапе 14. Новые фракции в `0.1.x` не планируются.

## 9. Технические неоднозначности и безопасные решения

| ID | Неоднозначность | Решение |
|---|---|---|
| A1 | В текущем окружении нет доступа к Forge/Gradle/Mojang и нет JDK 17 | Сборку не имитировать; этап 1 выполнить там, где есть JDK 17 и доступ (см. STATUS) |
| A2 | Установлена Java 21, цель — Java 17 | Использовать JDK 17; не собирать на 21 (ForgeGradle 6 для 1.20.1 рассчитан на 17); toolchain закрепить в `build.gradle` |
| A3 | Системный Gradle 8.14.3, MDK 1.20.1 использует свой Wrapper (по памяти — ветка 8.1.x; точную версию взять из MDK) | Брать Wrapper только из MDK; системный Gradle не использовать для сборки мода; не писать wrapper-файлы вручную |
| A4 | Пиглины, брюты, хоглины, зоглины, жители работают на `Brain`, не на `GoalSelector` | Две ветки интеграции (§2.8); для brain-мобов — `LivingChangeTargetEvent` + `ATTACK_TARGET`; проверить поведение игровым тестом |
| A5 | Нейтральные существа (зомбифицированный пиглин, векс, ведьма) не перечислены в задании | По умолчанию вне фракций; добавляются тегами/датапаком (ведьма — вопрос A21) |
| A6 | Приручённые существа и голем, созданный игроком | Не втягиваются по умолчанию (`combat.includeOwnedMobs=false`); игрок не фракция |
| A7 | Что считать «поселением» | Деревня ванильной структуры + POI-кластер (колокол/кровати/работа); обнаружение только в загруженных областях; ручной «знамя поселения» — возможное будущее расширение |
| A8 | Измерения войны | Театры: Overworld и Nether. Энд вне войны в 0.1.x. Данные одни, в Overworld |
| A9 | Пиглины и хоглины в Верхнем мире зомбифицируются (становятся зомби-пиглинами/зоглинами) через ~15 с | При материализации выставлять иммунитет (`setImmuneToZombification(true)`, `setImmuneToZoglification(true)` — сверить имена по исходникам 1.20.1); проверить в GameTest |
| A10 | Despawn боевых мобов и persistence | Материализованные: `setPersistenceRequired` на время боя + контролируемое удаление реконсилером; общий лимит из конфига |
| A11 | Лимиты мобов | Счётчики по событиям (`WarRuntime`); перед спавном проверка глобальных/локальных лимитов; не использовать ванильный mob cap как единственный ограничитель |
| A12 | API `SavedData` в 1.20.1 | `computeIfAbsent(load, factory, name)` + `setDirty()`; `SavedData.Factory` — API 1.20.2+, не использовать |
| A13 | `SERVER`-конфиг доступен только после старта мира | Не читать значения в `FMLCommonSetupEvent`/статических инициализаторах; кэшировать на `ServerStartingEvent` и при `ModConfigEvent.Reloading` |
| A14 | Dist-безопасность | См. §6 |
| A15 | Нет графических ассетов | Рендер на ванильных моделях; текстуры создаём как реальные PNG, либо этап 8 использует ванильные текстуры; не регистрировать ресурсы без файлов |
| A16 | Терминология: «ифрит» = Blaze, «поборник» = Vindicator, «разбойник» = Pillager, «вызыватель» = Evoker, «скелет-иссушитель» = Wither Skeleton | Зафиксировано в `CLAUDE.md` и в тегах |
| A17 | Покрытие обнаружения порталов (порталы, созданные командами/модами/мировой генерацией, не вызывают `PortalSpawnEvent`) | Комбинировать события создания, пересечения и локальный POI-поиск; не гарантировать «все порталы мира»; задокументировать |
| A18 | Время: офлайн-время не идёт, `/time add`/сон ускоряют | База — `overworld.getGameTime()`; хранить `lastSimGameTime`; ограничить догоняющие шаги |
| A19 | Условие старта войны: «игрок использует портал», но «мир меняется и без игрока» | Старт — первый зарегистрированный активный портал (любого происхождения); далее `autonomousProgression=true` ведёт войну сама; естественный старт без портала — `naturalRiftsEnabled=false` по умолчанию |
| A20 | Дублирование мобов при перезагрузке чанков с материализованными группами | `batchId` + список закрытых партий + поглощение при `EntityJoinLevelEvent` (§1.2) |
| A21 | Взаимодействие с ванильными рейдами (`Raid`, плохое знамение) и принадлежность ведьмы/иллюзиониста | Ванильные рейды не отключаем и не дублируем; их разорители автоматически входят в `ILLAGERS` по тегу; ведьма — отдельное решение на этапе 2 (по умолчанию в `ILLAGERS`) |
| A22 | Версии API Forge | Перед использованием любого API — сверка с исходниками Forge 1.20.1 (в частности, отмена/замена цели в `LivingChangeTargetEvent`, имена методов иммунитета к зомбификации) |

## 10. Что явно вне архитектуры 0.1.x

Новые измерения, структуры мира, новые блоки/предметы за пределами необходимых (журнал/яйца призыва), модификация ванильных структур, внешние API других модов.
