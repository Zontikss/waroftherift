# War of the Rift

Мод для Minecraft Java Edition: Верхний мир подвергается вторжению из Нижнего мира. Три фракции — Лига поселений, Орден Разорителей и Легион Разлома — воюют одновременно, и мир меняется даже без участия игрока.

| | |
|---|---|
| Mod ID | `waroftherift` |
| Версия мода | `0.1.0` (стартовый каркас) |
| Minecraft | 1.20.1 |
| Forge | 47.4.0 (диапазон `[47,)`) |
| Java | 17 |
| Сборка | Gradle Wrapper 8.1.1, ForgeGradle 6.0.x, official mappings |

## Статус

Сейчас это рабочий каркас: основной класс мода, `mods.toml`, сборка и CI. Игровые системы ещё не реализованы — план и архитектура в [`docs/PLAN.md`](docs/PLAN.md) и [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), фактический статус — в [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).

## Сборка без установки Java и Forge (GitHub Actions)

1. Запушьте репозиторий на GitHub (ветка `main`).
2. Откройте вкладку **Actions** → workflow **Build** → последний запуск.
3. После успешной сборки скачайте артефакт **waroftherift-jar** (внутри `waroftherift-0.1.0.jar`) и положите jar в папку `mods` клиента или сервера Forge 1.20.1.

## Локальная сборка

Нужны JDK 17 и доступ к `maven.minecraftforge.net`.

```bash
./gradlew build          # Linux / macOS
.\gradlew.bat build      # Windows
```

Готовый jar появится в `build/libs/`. Запуск для разработки: `./gradlew runClient`, `./gradlew runServer`, `./gradlew runGameTestServer`.

## Где что лежит

- Исходники: `src/main/java/io/github/waroftherift/`
- Ресурсы: `src/main/resources/` (`META-INF/mods.toml`, `pack.mcmeta`, `assets/waroftherift/`, `data/waroftherift/`)
- CI: `.github/workflows/build.yml`
- Конфигурация мода появится на этапе 2: игровые параметры — в файле мира `serverconfig/waroftherift-server.toml`.

## Правила проекта

См. [`CLAUDE.md`](CLAUDE.md).
