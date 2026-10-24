# Подготовка к этапу 2 — Конфигурация и фракции

Дата: 2026-10-24 · Предусловие: **этап 1 принят** — `./gradlew build` зелёный в GitHub Actions, jar скачан. Пока этого нет, код этапа 2 **не пишем**: иначе первая ошибка сборки будет неотличима от ошибки каркаса.

Цель этапа (из `docs/PLAN.md`): `WarOfTheRiftConfig`, `Faction`, `FactionResolver` на тегах, диагностическая команда `/waroftherift faction`.

## 1. Файлы этапа

| Файл | Назначение |
|---|---|
| `config/WarOfTheRiftConfig.java` | `ForgeConfigSpec`: SERVER-спека (игровые параметры) и CLIENT-спека (UI). Только ключи, нужные уже сейчас: `general.enabled`, `general.debugLogging`, `combat.includeOwnedMobs`. Остальные ключи из `ARCHITECTURE.md` §2.13 добавляются вместе с системами, которые их используют |
| `faction/Faction.java` | enum `SETTLEMENTS`, `ILLAGERS`, `NETHER_LEGION` + `NONE`-семантика через `Optional`/отдельное значение (решить при реализации; без `null`) |
| `faction/FactionResolver.java` | `EntityType`/`LivingEntity` → `Faction` по тегам; кэш по `EntityType`; сброс кэша на `TagsUpdatedEvent` и `ServerStoppedEvent`; исключения A6 (приручённые, голем игрока) |
| `data/waroftherift/tags/entity_types/faction/settlements.json` | `minecraft:villager`, `minecraft:iron_golem` (+ Settlement Guard позже, на этапе 8) |
| `.../illagers.json` | `minecraft:pillager`, `vindicator`, `evoker`, `ravager`, `illusioner` (+ решение по `witch`, A21) |
| `.../nether_legion.json` | `piglin`, `piglin_brute`, `hoglin`, `zoglin`, `ghast`, `blaze`, `magma_cube`, `wither_skeleton` (+ Rift Commander на этапе 8) |
| `command/WarOfTheRiftCommands.java` | только `/waroftherift faction <entity>` и `/waroftherift status` (заглушек нет: `status` показывает реальные значения конфига) |
| `event/WarForgeBusEvents.java`, `event/WarModBusEvents.java` | единственные точки подписки; на этапе 2 — `RegisterCommandsEvent`, `TagsUpdatedEvent`, `ServerStoppedEvent` |
| `en_us.json` / `ru_ru.json` | ключи сообщений команды и названий фракций — только реально используемые |

`WarOfTheRiftMod` получает: `ModLoadingContext.get().registerConfig(...)` (по одному вызову на тип), регистрацию двух классов событий.

## 2. API, которые нужно сверить с исходниками Forge 1.20.1 до написания кода

Эти пункты помечены в моей памяти как неподтверждённые; ошибка компиляции здесь вероятнее всего.

1. `ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, spec)` и `ForgeConfigSpec.Builder` — сигнатуры `defineInRange`, `comment`, `translation`.
2. `ModConfigEvent.Loading` / `Reloading` — пакет `net.minecraftforge.fml.event.config.ModConfigEvent`; чтение SERVER-значений только после загрузки (A13).
3. `TagKey<EntityType<?>>` создаётся через `TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(modId, path))`; проверка `EntityType#is(TagKey)`. В 1.20.1 `new ResourceLocation(String, String)` — публичный конструктор (фабрика `fromNamespaceAndPath` появилась позже).
4. `TagsUpdatedEvent` — пакет `net.minecraftforge.event.TagsUpdatedEvent`, поле `getUpdateCause()`.
5. Brigadier: `Commands.argument(..., ResourceArgument.resource(context, Registries.ENTITY_TYPE))` либо `EntitySummonArgument`; `SuggestionProviders.SUMMONABLE_ENTITIES`. Выбрать один вариант по исходникам.
6. `RegisterCommandsEvent#getDispatcher()` и `CommandSourceStack#hasPermission(2)`.
7. `TamableAnimal#isTame`, `OwnableEntity#getOwnerUUID` для исключений A6; `IronGolem#isPlayerCreated`.

## 3. Решения, которые нужно принять до кода

| Вопрос | Предлагаемое решение |
|---|---|
| Тип конфига | SERVER (игровое), CLIENT (UI). COMMON не используем |
| Ведьма (`witch`) | в `ILLAGERS` по умолчанию, меняется датапаком; зафиксировать в журнале решений |
| Зомбифицированный пиглин | вне фракций (A5) |
| Отсутствие фракции | `Optional<Faction>` из резолвера, не `null` и не «магическое» значение |
| Как тестировать | Чистая логика вынесена так, чтобы не требовать Minecraft: `Faction` и функция «тег → фракция» над строковым id. `FactionResolver` над реальными `EntityType` проверяется GameTest/ручным сценарием, а не JUnit |

## 4. Тесты и сборка

- Добавить в `build.gradle` **только** `testImplementation` JUnit 5 + `test { useJUnitPlatform() }` (единственная допустимая сторонняя зависимость, см. `CLAUDE.md` §2). Проверить совместимость с ForgeGradle 6 в CI: если `./gradlew build` ломается из-за тестовой конфигурации — откатить, а не отключать проверки.
- Ручной сценарий (G5), выполняет пользователь в клиенте/на сервере: `/waroftherift faction minecraft:piglin` → `NETHER_LEGION`; `... minecraft:villager` → `SETTLEMENTS`; `... minecraft:cow` → «нет фракции»; переопределение тега датапаком меняет результат после `/reload`.

## 5. Критерии приёмки этапа 2

1. `./gradlew build` зелёный в Actions, jar в артефактах.
2. `./gradlew runServer` стартует без клиентских классов (проверяет пользователь или окружение с доступом) — **не заявлять до реального запуска**.
3. Конфиг создаётся при первом запуске мира: `serverconfig/waroftherift-server.toml`.
4. Теги валидны; неизвестная сущность → нет фракции; игрок не имеет фракции.
5. `docs/IMPLEMENTATION_STATUS.md` обновлён фактическими результатами.

## 6. Риски

- Каркас этапа 1 написан по памяти; первая ошибка Actions может потребовать правок `build.gradle` ещё до этапа 2.
- Чтение SERVER-конфига до старта мира даёт исключение — не обращаться к значениям из статических инициализаторов.
- Без доступа к GitHub этот этап можно только подготовить, но не принять.
