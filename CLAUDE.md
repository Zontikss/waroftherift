# CLAUDE.md — War of the Rift

Этот файл читается в начале каждой сессии. Подробности: `docs/PLAN.md`, `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION_STATUS.md`.
Если файл расходится с реальным состоянием диска — прав диск; исправь документ.

## 1. Проект

**War of the Rift** — мод для Minecraft Java Edition. Верхний мир подвергается вторжению из Нижнего мира. Три фракции воюют одновременно, мир меняется и без участия игрока.

- Mod ID: `waroftherift`
- Название: War of the Rift
- Базовый пакет: `io.github.waroftherift`
- Версия разработки: `0.1.0`

Фракции (enum `Faction`):

| Фракция | Название | Состав (по умолчанию) |
|---|---|---|
| `SETTLEMENTS` | Лига поселений | житель, железный голем, Settlement Guard (новый) |
| `ILLAGERS` | Орден Разорителей | разбойник (pillager), поборник (vindicator), вызыватель (evoker), разоритель (ravager), иллюзионист, иные рейдовые |
| `NETHER_LEGION` | Легион Разлома | пиглин, пиглин-брют, хоглин, зоглин, гаст, ифрит (= Blaze), магмовый куб, скелет-иссушитель, Rift Commander (новый) |

Игрок не обязан участвовать в войне.

## 2. Платформа (целевая)

- Minecraft Java Edition **1.20.1**
- Minecraft Forge **47.x** (точную сборку фиксируем на этапе 1, сверяясь с официальным MDK)
- **Java 17** (toolchain). Java 21 не использовать для сборки.
- Gradle Wrapper из официального MDK 1.20.1 (ForgeGradle 6.x)
- Mappings: official (Mojang), как в MDK
- Только Forge. **Не** Fabric, **не** NeoForge. Сторонние библиотеки — только при реальной необходимости (исключение, допустимое для тестов: JUnit, только `testImplementation`).
- API других версий Minecraft без проверки совместимости не использовать. Перед использованием API сверяться с Forge 1.20.1 и исходниками проекта (например, `SavedData` в 1.20.1 создаётся через `computeIfAbsent(load, factory, name)`, а не через `SavedData.Factory`).

## 3. Текущее состояние

Смотри `docs/IMPLEMENTATION_STATUS.md` — единственный журнал истины о том, что готово. Коротко на 2026-10-24: этап 0 (аудит и документация) выполнен; каркас этапа 1 создан (Forge 47.4.0, Gradle Wrapper 8.1.1, CI в `.github/workflows/build.yml`), но **сборка ещё не проверена** — она выполняется в GitHub Actions, локально в окружении Claude недоступна (см. «Ограничения окружения»).

## 4. Окончательные имена (использовать последовательно)

```
io.github.waroftherift
  WarOfTheRiftMod
  registry/    ModEntities, ModItems, ModBlocks            (только реально существующие объекты)
  faction/     Faction, FactionResolver
  data/        WarSavedData
  data/model/  PortalRecord, SettlementRecord, FrontRecord, ForceRecord, WarEventRecord, WarState
  runtime/     WarRuntime                                  (временные кэши сервера, чистятся при остановке)
  portal/      PortalRegistry
  settlement/  SettlementManager, SettlementScanner
  simulation/  WarSimulationManager, StrategicBattleResolver, WarRandom
  combat/      FactionCombatSystem, FactionRelation, ai/
  invasion/    InvasionManager, InvasionStage
  battle/      BattleMaterializer, BattleReconciler       (граница «стратегия ↔ реальность»)
  journal/     WarEventLog, WarSnapshot, WarSnapshotFactory
  network/     NetworkHandler, packet/
  config/      WarOfTheRiftConfig
  command/     WarOfTheRiftCommands
  entity/      SettlementGuardEntity, RiftCommanderEntity, ai/
  event/       WarModBusEvents, WarForgeBusEvents          (единственные точки подписки, Dist-нейтральные)
  client/      ClientModEvents, ClientForgeEvents, ClientWarState
  client/journal/ WarJournalScreen
```

Классы создаются **по мере реализации системы**, не «для дерева файлов».

## 5. Жёсткие архитектурные правила

1. Источник истины — сервер. Клиент рисует UI и принимает ввод.
2. Глобальная война хранится в `WarSavedData`, привязанном к **Overworld** (`server.overworld().getDataStorage()`).
3. Сохраняемые модели содержат только примитивы, строки, `UUID`, id измерений/сущностей как строки/`ResourceLocation`, `BlockPos`/long. **Никаких** ссылок на `Level`, `Entity`, `Player`, `ServerLevel`.
4. Стратегический расчёт **не** создаёт мобов, **не** загружает чанки, **не** ломает блоки. Реальные группы живут только в загруженных областях рядом с игроком.
5. У каждого фронта в любой момент ровно один «авторитет»: `STRATEGIC` или `REAL`. Переходы — через `battle/` с гистерезисом.
6. Последствия детерминированы (сид = f(seed мира, id, шаг)) и сохраняются.
7. Мод не уничтожает постройки игрока без необходимости; по умолчанию блоки не разрушаются (`consequences.allowBlockDamage = false`).
8. Любая система, влияющая на производительность, имеет лимит в `WarOfTheRiftConfig`.
9. UI не меняет серверные данные напрямую: только пакеты `NetworkHandler`; сервер проверяет права и валидирует.
10. Запрещено: полный перебор сущностей мира каждый тик; глобальный поиск блоков по измерениям; бесконечный спавн; случайные события «на каждом тике»; дублирующиеся подписки на события; пустые `catch`; пустые `TODO` вместо реализации; клиентские классы на выделенном сервере; статическое глобальное изменяемое состояние, переживающее остановку сервера.
11. Учёт живых боевых мобов — по событиям (`EntityJoinLevelEvent`, смерть, выход) и счётчикам в `WarRuntime`, а не сканированием.
12. Ядро симуляции (`simulation/`, `data/model/`) по возможности не зависит от классов Minecraft, чтобы тестироваться через JUnit без запуска игры.

## 6. Правила работы

- Перед любым изменением: изучить папку; найти `build.gradle`, `gradle.properties`, `settings.gradle`, `gradlew`/`gradlew.bat`, `mods.toml`, исходники; прочитать этот файл и `docs/*`.
- Не удалять и не перезаписывать рабочую реализацию без причины. Не перезаписывать ресурсы пользователя.
- Не утверждать, что сборка/тест/запуск выполнены, если команда не была реально выполнена. В `IMPLEMENTATION_STATUS.md` писать точную команду и фактический результат.
- Не отключать проверки компиляции ради «зелёной» сборки; не подделывать wrapper-файлы; не регистрировать несуществующие ресурсы (модели, текстуры, звуки).
- Ошибки не подавлять; логировать через `LogUtils.getLogger()`.
- После этапа — обновить `docs/IMPLEMENTATION_STATUS.md` (готово / не готово / известные ошибки / следующий шаг).
- Язык документов и комментариев высокого уровня — русский; идентификаторы кода — английский; локализация — `ru_ru.json` и `en_us.json`.

## 7. Команды (после появления Gradle Wrapper)

Linux/macOS: `./gradlew build` · `./gradlew runServer` · `./gradlew runClient` · `./gradlew runGameTestServer`
Windows: `.\gradlew.bat build` и т. д.
Фактические результаты — только в `docs/IMPLEMENTATION_STATUS.md`.

## 8. Ограничения окружения (на 2026-10-24)

- Рабочая папка `/home/claude` была пуста: ни проекта, ни документов.
- Установлен только JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`); JDK 17 отсутствует. Системный Gradle 8.14.3, Wrapper нет.
- Исходящий доступ закрыт политикой прокси для `maven.minecraftforge.net`, `files.minecraftforge.net`, `services.gradle.org`, `repo.maven.apache.org`, `piston-meta.mojang.com` (403 на CONNECT). Скачать MDK, Forge и Minecraft здесь нельзя; повторными запросами это не обходится.
- Следствие: сборку и запуск нужно выполнять на машине пользователя (JDK 17 + доступ к Forge Maven) либо в окружении с разрешённым доступом. Не имитировать их результат.
