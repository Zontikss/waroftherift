# IMPLEMENTATION STATUS — War of the Rift

Последнее обновление: 2026-10-24 · Текущий этап: **0 завершён**; **этап 1 — файлы каркаса созданы, сборка ещё НЕ проверена** (ждёт первого запуска GitHub Actions).

## 0. Обновление этапа 1 (2026-10-24, вечер)

Создан каркас Forge 1.20.1 / 47.4.0 в каталоге `waroftherift/`:
`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `META-INF/mods.toml`, `pack.mcmeta`, `WarOfTheRiftMod.java`, пустые `en_us.json`/`ru_ru.json` (ключей пока нет), `.github/workflows/build.yml`, `.gitignore`, `.gitattributes`, `README.md`.

- **Откуда взяты файлы.** Официальный MDK скачать нельзя (доступ закрыт). `build.gradle`, `settings.gradle`, `gradle.properties`, `mods.toml` и `pack.mcmeta` написаны **по памяти** по структуре MDK 1.20.1-47.x; это не копия официального MDK. Wrapper сгенерирован штатной командой `gradle wrapper --gradle-version 8.1.1 --distribution-type bin --no-validate-url` (Gradle 8.14.3) — файлы настоящие, но версия Gradle 8.1.1 выбрана по памяти как совместимая с ForgeGradle `[6.0,6.2)`.
- **Версия Forge.** 47.4.0 — подтверждена объявлением на forums.minecraftforge.net как рекомендованная сборка для 1.20.1 (от 4 января 2025). Существуют и более новые 47.4.x; обновление — отдельное решение.
- **Репозиторий GitHub не создан и ничего не запушено**: в сессии нет GitHub-коннектора, токен `gh` недействителен, единственный доступный репозиторий (`Zontikss/airi`) к моду не относится.

Проверено реально (без сети): `mods.toml` после подстановки свойств разбирается как TOML (modId `waroftherift`, зависимости `forge`, `minecraft`, `loaderVersion [47,)`); `pack.mcmeta` и оба lang-файла — валидный JSON; `build.yml` — валидный YAML (1 job, 7 шагов); `gradle-wrapper.jar` — целый zip с `GradleWrapperMain`; `gradlew` исполняемый; `mod_id` согласован в Java, `gradle.properties` и `mods.toml`; ссылок на NeoForge/Fabric в файлах сборки и исходниках нет.

**Не проверено:** компиляция Java, разрешение плагина ForgeGradle, `./gradlew build`, запуск мода. Первый запуск Actions может выявить ошибки в файлах, написанных по памяти — они будут исправляться по логу.

## 1. Сводка

| Параметр | Значение |
|---|---|
| Minecraft / Forge / Java (цель) | 1.20.1 / 47.4.0 / 17 |
| Обнаружено в проекте (на момент этапа 0) | проекта нет (папка пуста) |
| Обнаружено в окружении | JDK 21.0.12 (JDK 17 нет), системный Gradle 8.14.3, Git 2.43.0 |
| Gradle Wrapper | сгенерирован (8.1.1), в репозитории; не запускался |
| Сборка | **не выполнялась** (нечего собирать; Gradle-проекта нет) |
| Запуск в Minecraft | **не выполнялся** |
| Игровая логика | не добавлялась |

## 2. Результаты аудита (этап 0)

Состояние рабочей папки `/home/claude` на 2026-10-24:

| Объект | Найдено |
|---|---|
| `build.gradle`, `gradle.properties`, `settings.gradle` | нет |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | нет |
| `src/` (в т. ч. `META-INF/mods.toml`, основной класс) | нет |
| `CLAUDE.md`, `docs/*` до этапа 0 | нет (созданы на этапе 0) |
| Git-репозиторий | нет (`not a git repository`) |
| Ресурсы / файлы пользователя | нет (в папке только служебные каталоги окружения `.cache`, `.config`, `.claude`, `.npm*`, `.ssh`, `.local`, `.gitconfig`, `.wget-hsts`; не тронуты) |

Файл-вложение с описанием этапа 2 («Подготовить рабочий каркас…») прочитан. Он требует предварительно прочитать `CLAUDE.md` и `docs/*`, которых на момент аудита не было; сейчас они созданы. Сам этап 2 из вложения (каркас проекта) в рамках этапа 0 **не выполнялся**, так как этап 0 запрещает добавлять игровые системы, а сборка заблокирована (см. §3). В `PLAN.md` он соответствует этапу 1.

Вывод: **проект не готов к разработке игровой логики**. Нет самого Gradle-проекта. Документация готова, архитектура зафиксирована.

## 3. Блокеры и риски

| # | Тип | Описание | Влияние | Безопасное решение |
|---|---|---|---|---|
| B1 | Блокер (окружение) | Исходящий доступ к `maven.minecraftforge.net`, `files.minecraftforge.net`, `services.gradle.org`, `repo.maven.apache.org`, `piston-meta.mojang.com` закрыт политикой прокси (CONNECT → 403; curl вернул код 000). `github.com` ответил 403 на `HEAD`. | Нельзя скачать MDK, Forge, Gradle distribution, Minecraft и mappings | Выполнить этап 1 на машине пользователя или в окружении, где эти хосты разрешены. Не повторять запросы (прокси запрещает ретраи при policy-denial). Не подделывать wrapper |
| B2 | Блокер (окружение) | JDK 17 отсутствует (есть только 21) | Целевая сборка Forge 1.20.1 требует Java 17 | Установить JDK 17 (Temurin/OpenJDK) и указать toolchain 17 |
| B3 | Блокер (доступ) | Репозиторий `Zontikss/waroftherift` создан пользователем (1 коммит «Initial commit», только `README.md`) и подключён к сессии через `add_repo`; чтение/клонирование работает. Запись запрещена: приложение Claude на GitHub не имеет доступа к репозиторию (`git push` → HTTP 403, «Claude doesn't have GitHub access to Zontikss/waroftherift»). Локальная история уже слита с удалённым коммитом (merge `4e16395`), push будет fast-forward. Push не выполнен | Нет репозитория с кодом и нет запуска Actions | Пользователь проверяет, что репозиторий создан, и выдаёт GitHub-приложению Claude доступ к нему (Settings → Applications → Claude → Repository access); либо загружает zip вручную. Обход прокси токеном не применялся |
| R1 | Риск | Системный Gradle 8.14.3 ≠ версия Wrapper из MDK 1.20.1 (ветка 8.1.x); ForgeGradle 6 для 1.20.1 с новым Gradle не проверялся | Возможны ошибки сборки при использовании системного Gradle | Использовать только Wrapper из MDK (A3) |
| R2 | Риск | Мобы на `Brain` (пиглины, хоглины, жители) не управляются через `GoalSelector` | Базовая схема целей ИИ не сработает для части Легиона | A4: отдельная ветка через `LivingChangeTargetEvent`/`ATTACK_TARGET`, игровая проверка |
| R3 | Риск | Зомбификация пиглинов/хоглинов в Верхнем мире | Легион «превратится» в зомби-пиглинов | A9: иммунитет при материализации, GameTest |
| R4 | Риск | Нет графических ассетов для двух новых мобов | Битые текстуры/рендер | A15: ванильные модели + реально созданные PNG |
| R5 | Риск | Покрытие обнаружения порталов неполное | Часть порталов не попадёт в реестр | A17: комбинированный подход, документировать ограничения |
| R6 | Риск | Часть API 1.20.1 указана по памяти (напр., имена методов иммунитета, возможности отмены цели) | Ошибки компиляции/поведения | A22: сверка с исходниками Forge 1.20.1 перед использованием |

Технические неоднозначности A1–A22 и принятые решения — `docs/ARCHITECTURE.md` §9.

## 4. Команды, реально выполненные на этапе 0

Все — в окружении Linux (`Linux 6.18.44-fc-v153 x86_64`), рабочая папка `/home/claude`.

| Команда (суть) | Результат |
|---|---|
| Просмотр содержимого папки (`Glob *`, затем `ls -la`) | Файлов проекта нет; только служебные каталоги окружения |
| `uname -a` | Linux, x86_64 |
| `java -version` | OpenJDK 21.0.12.1 (Ubuntu); JDK 17 не найден |
| `gradle --version` | Gradle 8.14.3 (системный) |
| `git --version` / `git rev-parse --is-inside-work-tree` | Git 2.43.0; папка не является репозиторием |
| `ls /usr/lib/jvm`, `update-alternatives --list java`, `echo $JAVA_HOME` | Установлены только `java-21-openjdk-amd64` и `java-1.21.0-openjdk-amd64`; `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| `find` по `gradlew`, `build.gradle`, `mods.toml`, `CLAUDE.md` | ничего не найдено |
| `curl -I` к `maven.minecraftforge.net`, `files.minecraftforge.net`, `services.gradle.org`, `repo.maven.apache.org`, `piston-meta.mojang.com` | код `000` (соединение не установлено) |
| `curl -I https://github.com/MinecraftForge/MinecraftForge` | HTTP `403` |
| `curl "$HTTPS_PROXY/__agentproxy/status"` | прокси включён; в `recentRelayFailures` — `connect_rejected` (403 на CONNECT) для `maven.minecraftforge.net`, `files.minecraftforge.net`, `services.gradle.org` |

**Не выполнялись:** `./gradlew build`, `./gradlew runServer`, `./gradlew runClient`, любые тесты (Gradle-проекта нет).

## 5. Статус этапов

| Этап | Название | Статус |
|---|---|---|
| 0 | Аудит и документация | ✅ выполнен 2026-10-24 |
| 1 | Каркас проекта и сборка | 🟡 файлы созданы; сборка проверяется в GitHub Actions (локально заблокирована B1, B2) |
| 2 | Конфигурация и фракции | ⬜ не начат |
| 3 | Сохраняемое состояние и журнал | ⬜ не начат |
| 4 | Порталы и старт вторжения | ⬜ не начат |
| 5 | Поселения | ⬜ не начат |
| 6 | Модель вторжения | ⬜ не начат |
| 7 | Стратегическая симуляция | ⬜ не начат |
| 8 | Новые существа | ⬜ не начат |
| 9 | Боевое взаимодействие фракций | ⬜ не начат |
| 10 | Материализация боёв | ⬜ не начат |
| 11 | Последствия войны | ⬜ не начат |
| 12 | Сеть и журнал (UI) | ⬜ не начат |
| 13 | Команды и диагностика | ⬜ не начат |
| 14 | Баланс и надёжность | ⬜ не начат |
| 15 | Релиз 0.1.x | ⬜ не начат |

## 6. Статус классов

Написан (не собирался): `WarOfTheRiftMod` — `@Mod`, mod event bus, лог в `FMLCommonSetupEvent`. Остальное — только фиксация плана (все ⬜ «не реализован»):

`ModEntities`, `ModItems`, `ModBlocks`, `Faction`, `FactionResolver`, `WarSavedData`, `data/model/*`, `WarRuntime`, `PortalRegistry`, `SettlementManager`, `SettlementScanner`, `WarSimulationManager`, `StrategicBattleResolver`, `WarRandom`, `FactionCombatSystem`, `FactionRelation`, `InvasionManager`, `InvasionStage`, `BattleMaterializer`, `BattleReconciler`, `WarEventLog`, `WarSnapshot`, `WarSnapshotFactory`, `NetworkHandler`, `WarOfTheRiftConfig`, `WarOfTheRiftCommands`, `SettlementGuardEntity`, `RiftCommanderEntity`, `WarModBusEvents`, `WarForgeBusEvents`, `ClientModEvents`, `ClientForgeEvents`, `ClientWarState`, `WarJournalScreen`.

Созданные файлы: `CLAUDE.md`, `docs/PLAN.md`, `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION_STATUS.md`.

## 7. Известные ошибки

Нет (кода нет). Известные внешние проблемы — B1, B2.

## 8. Следующий шаг

**Подготовка этапа 2:** `docs/STAGE_2_PREPARATION.md` (файлы, API для сверки, решения, приёмка). Код этапа 2 не пишется, пока этап 1 не принят зелёной сборкой.

**Актуально:** выдать доступ к репозиторию (B3), затем (если он ещё не создан) создать на GitHub пустой репозиторий, залить в него содержимое `waroftherift/`, дождаться запуска **Actions → Build**, скачать артефакт `waroftherift-jar`; результат (успех или текст ошибки) записать в этот файл. После зелёной сборки — `runServer` (G2) на машине с доступом, затем этап 2.

Исходный план этапа 1 (оставлен для справки). Предусловия:
1. JDK 17 установлен и используется Gradle.
2. Доступ к Forge Maven, Gradle distributions, Mojang (или заранее скачанный официальный MDK Forge 1.20.1-47.x, загруженный пользователем).

Порядок:
1. Если у пользователя есть распакованный MDK — положить его в корень проекта (реальный `gradlew`, `gradle/wrapper/*`, `build.gradle`, `gradle.properties`, `settings.gradle`, `src/`); иначе — получить его из официальных источников на машине с доступом.
2. Привести `gradle.properties` к `mod_id=waroftherift`, `mod_name=War of the Rift`, `mod_version=0.1.0`, `mod_group_id=io.github.waroftherift`, mappings `official`/`1.20.1`; проверить Forge 47.x и toolchain 17.
3. Создать `WarOfTheRiftMod`, `mods.toml` (Forge `[47,)`, Minecraft `[1.20.1,1.21)`), ресурсы, `en_us.json`/`ru_ru.json`, `README.md`.
4. `./gradlew build`, затем `./gradlew runServer`; результаты записать в этот файл.

Если MDK получить нельзя — не создавать «ненастоящие» файлы; можно заранее подготовить только то, что корректно без сборки (`README.md`, структура каталогов ресурсов, JSON локализации).
