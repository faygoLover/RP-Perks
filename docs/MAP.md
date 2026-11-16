# RP Perks — карта

Версия на момент написания: 2.11.2 (16.11.2026). Факты по классам, событиям, пакетам — `docs/INDEX.md`.

## 1. Что делает и чего не делает

Карточка персонажа: **особенности** (перки — положительные за баллы, отрицательные дают баллы, умения,
служебные), **характеристики** от них (здоровье, скорость, броня… — атрибуты ванили) и **навыки** 0–10
(Медицина, Кулинария, оружейные, ближний бой, Сила, Телосложение), которые растут от практики и падают без
неё. Игрок выбирает особенности сам в окне `/rpperks` по правилам, ГМ правит чужую карточку. Для остальных
модов семейства это поставщик черт и навыков RP Core (`TraitProvider`).

За этим — не сюда:
- **механику большинства особенностей даёт другой мод**: Perks говорит «у игрока есть черта», а что она значит —
  решает Medicine, Culinary, Psyche, Stamina, Needs… Кто за какую особенность отвечает — `perk/PerkConsumers`;
- откуда берётся опыт навыка — из модов (`RpCoreAPI.practice`, `grantXp`); здесь только учёт и рост;
- грудь и фигура (мод RP Figure) — отложено Алексом, не развивать.

## 2. Как течёт работа

**Выбор особенностей.** `/rpperks` → `commands/PerkCommands` → пакет `OpenPerkScreenPacket` → клиент
`client/screen/PerkScreen` → кнопка → `PerkActionPacket` на сервер → `events/PerkActions`: проверка правил
(`perk/PerkRules`: баллы, несовместимые, можно ли менять) → `PlayerPerks.grantPerk / removePerk` →
`PerkEventHandler.refreshPlayer` → `SyncPerksPacket` клиенту (и админу, если он смотрит эту карточку).

**Пересчёт.** `events/PerkEventHandler.refreshPlayer(игрок, данные)`: снять динамические модификаторы →
`SkillService.syncPhysique` и `syncSkillPerks` (особенность сдвигает стартовый уровень навыка) → для каждой
характеристики `perk/Stat`: база из конфига + поправки особенностей → атрибут игрока → синхронизация.
Зовётся на входе, возрождении, после любой правки карточки, после вайпа.

**Механики самого Perks.** `events/PerkMechanicsHandler`: тик (кофеин, солнце, бег…), еда, лечение, урон,
отдача, падение, цели мобов, неуклюжесть. Только те особенности, у которых в `PerkConsumers` стоит `SELF`.

**Навыки.** практика из любого мода → `RpCoreAPI.practice / grantXp` → `compat/CoreTraits` (поставщик) →
`skill/SkillService.practice / grantXp`: множитель опыта (талант, анкета тела, `RpCoreAPI.learningFactor`),
потолок (`cap`) → уровень. Раз в минуту времени тела — `SkillService.minuteTick`: плохое питание и болезнь
отнимают Силу и Телосложение. Баффы Силы и Телосложения — `skill/PhysicalEffects`; ближний бой и оружие —
`skill/SkillEvents`, `compat/tacz/WeaponSkills`. Клиенту — `SyncSkillsPacket`.

**Черты для семейства.** мод спрашивает `RpCoreAPI.hasTrait(игрок, id)` → `CoreTraits.hasTrait`: таблица
`TRAITS` «черта RP Core → особенности, которые её дают». Знание (служебная особенность, выдаётся в игре) —
`RpCoreAPI.grantKnowledge` → `CoreTraits.teach` → `grantKnowledge`.

**Данные игрока.** `capability/PlayerPerksProvider.register()` → `PlayerStore` RP Core; карточка переживает и
смерть, и возврат из Края. `PerkEventHandler.resync`: вход (миграция уровней ГМа → `refresh` → сообщение о
возврате очков), возрождение (`refresh`), смена измерения (отправить карточку и навыки заново).

## 3. Карта пакетов

| Пакет | За что отвечает | С чего начать читать |
|---|---|---|
| `rpperks` | вход мода и весь серверный конфиг | `Rpperks`, `Config` (описание 84 настроек, не логика) |
| `perk` | перечни: особенности, характеристики, правила выбора, кто даёт механику | `Perk`, `Stat`, `PerkRules`, `PerkConsumers` |
| `capability` | данные карточки, запись и миграция версий | `PlayerPerks`, `PlayerPerksProvider` |
| `events` | жизнь игрока и пересчёт, кнопки окна, механики особенностей | `PerkEventHandler`, `PerkActions`, `PerkMechanicsHandler` |
| `skill` | навыки: перечень, рост, потолки, баффы | `SkillDef`, `SkillService`, `PhysicalEffects`, `SkillEvents` |
| `compat` | RP Perks для RP Core: черты, навыки, знания; поправки инвентаря | `CoreTraits`, `CoreInventory` |
| `compat.tacz` | оружейные навыки для TaCZ (грузится, если мод стоит) | `WeaponSkills` |
| `network` | пакеты окна и синхронизации | `NetworkHandler` |
| `client`, `client.screen` | окно перков, клиентские данные | `PerkScreen` (1076 строк, одно окно на три вкладки) |
| `commands` | `/rpperks`, `/rpperks admin`, `/rpperks audit` | `PerkCommands` |
| `gametest` | игровые тесты | `PerksEdgeTests`, `SkillGameTests`, `PerkGameTests`, `NbtCoverage` |

## 4. Хочу добавить… — рецепты

| Что | Где | Образец | Не забыть |
|---|---|---|---|
| новую особенность | 1) значение в `perk/Perk` (id, категория, цена, название; последним параметром — id мода, без которого она пустая); 2) настройки в `Config` (раздел её категории); 3) `perk/PerkConsumers` — кто даёт механику; 4) если это черта для других модов — строка в `CoreTraits.buildTraits` и id черты в `RpIds` RP Core; 5) lang ru и en | `SECOND_WIND` (механика в RP Stamina), `UNFUSSY` (RP Needs) | тест `PerkGameTests` упадёт, если особенность не вписана в `PerkConsumers`; несовместимые — раздел `conflicts` конфига |
| механику особенности в самом Perks | обработчик в `events/PerkMechanicsHandler`; в `PerkConsumers` — `SELF` | «Неуклюжий» → `onClumsyUse` | проверять особенность через данные карточки, не через атрибуты |
| механику особенности в другом моде | в том моде: `RpCoreAPI.hasTrait(игрок, <id черты из RpIds>)` | Medicine и `RpIds.TOUGH` | здесь — только строка в `TRAITS` и `PerkConsumers` |
| поправку особенности к характеристике | `Config` (значение у особенности) + чтение в `PerkEventHandler.refreshPlayer` | `ATHLETIC` | новая характеристика — значение в `perk/Stat` (атрибут, границы) |
| новый навык | `skill/SkillDef` (id из `RpIds` RP Core, вид) + настройки в разделе `skills` конфига | `COOKING` | кто даёт опыт — другой мод через `RpCoreAPI.practice`; вкладка «Навыки» покажет сама |
| знание, выдаваемое в игре | служебная особенность (`Category.SERVICE`) + строка в `TRAITS`; выдаёт мод через `RpCoreAPI.grantKnowledge` | знания групп экзотики RP Culinary | — |
| новое поле карточки | `capability/PlayerPerks`: поле, `saveNBT`, `loadNBT`, `copyFrom`, `wipe` | `migrationRefunded` | формат меняется несовместимо — поднять `DATA_VERSION` и дописать миграцию в `loadNBT`; тест `NbtCoverage` |
| поправку к ячейкам инвентаря от особенности | `compat/CoreInventory` | — | через `InventoryModifier` RP Core |
| кнопку или вкладку окна | `client/screen/PerkScreen` + действие в `events/PerkActions` + поле в `PerkActionPacket` | — | сервер проверяет всё сам; экран без автотестов — проверка в игре |

## 5. Данные

| Что | Где хранится | Ключ | Что при смерти | Как попадает клиенту |
|---|---|---|---|---|
| карточка: особенности, баллы, навыки, счётчики | у игрока, `PlayerStore` RP Core | `rpperks:player_perks` | остаётся целиком | `SyncPerksPacket`, `SyncSkillsPacket` |
| чьё окно открыл админ | память сервера, `PerkEventHandler.ADMIN_SCREENS` | — | — | `OpenPerkScreenPacket` |

Версия формата — поле `perksVersion` в теге (`PlayerPerks.DATA_VERSION = 3`), миграции — в `PlayerPerks.loadNBT`.
`PlayerStore.dataVersion` RP Core **не подключать**: двух счётчиков версий в одном теге быть не должно.
Не сохраняются нарочно: `runnerCooldown`, `sprintSeconds`. Ключи и id особенностей не переименовывать.

## 6. Связи

- Берёт из Core: `body()` и `learningFactor`, `nutrition()`, `bodyState`, `profile`, `bodyRate`, `stamina()`;
  события `RpConsumeEvent`, `RpCharacterWipeEvent`, `BodyProfileChangedEvent`; `PlayerStore`.
- Даёт в Core: `TraitProvider` (`compat/CoreTraits`: навыки, черты, знания), `InventoryModifier`
  (`compat/CoreInventory`).
- Соседей по семейству не импортирует: что особенность значит в Medicine или Stamina, решают они сами по черте.
- Чужие: TaCZ — оружейные навыки (`compat/tacz/WeaponSkills`, при `isLoaded`).

## 7. Конфиги

`serverconfig/rpperks-server.toml` — `Config` (269 ключей, обычный `ForgeConfigSpec`): `general`, `selection`
(правила выбора), `baseline` (базовые характеристики), особенности по категориям, `skills`, `physical_decay`,
`conflicts`. Для новых миров готовый файл можно положить в `defaultconfigs`. Заплатки сервера —
`S5 Packs/layers/server/files/serverconfig`.

## 8. Сеть

`network/NetworkHandler`. Клиенту: `OpenPerkScreenPacket`, `SyncPerksPacket`, `SyncSkillsPacket`. На сервер:
`PerkActionPacket` — кнопка окна; сервер не верит ни баллам, ни правам: всё проверяется в `PerkActions`
(правила выбора, уровень прав для чужой карточки).

## 9. Ловушки

- **Особенность без кода — не ошибка, но должна быть записана:** в `PerkConsumers` стоит `RP` (только отыгрыш) или
  id мода. `/rpperks audit` показывает особенности, для которых сейчас не загружен ни один мод с механикой.
- **`CoreTraits.grantKnowledge` — статический метод**, а розетка RP Core называется `teach`: одноимённый метод в
  интерфейсе сломал бы компиляцию. Не переименовывать обратно.
- **Смена измерения:** клиент пересоздаёт игрока и теряет карточку — её и навыки надо отправить заново
  (`PerkEventHandler.resync`, ветка `DIMENSION`).
- **Уровень ГМа больше не замораживает навык** (с 2.7.0): ГМ меняет наработанный уровень, дальше навык растёт
  сам; старое поле переводится на входе (`SkillService.migrateGmLevels`) — порядок «миграция → refresh» не менять.
- **Сила и Телосложение идут по времени тела** (`SkillService.bodyMinute` = `RpCoreAPI.bodyRate()`): на паузе стоят.
- `PerkScreen` — один класс на три вкладки, автотестов нет: править точечно, проверять в игре.

## 10. Тесты

`python ../_family/family.py verify --tests "RP Perks"` — 12 игровых тестов: `PerksEdgeTests` + `NbtCoverage`
(каждое поле в сохранении, смерть, возврат из Края, перезаход), `SkillGameTests` (уровень ГМа, рост),
`PerkGameTests` (каждая особенность вписана в `PerkConsumers`).

## 11. Документы в docs/

| Файл | Состояние |
|---|---|
| `MAP.md`, `INDEX.md`, `test_findings.md` | действуют |
| `perks_audit_2026-11-06.md` | аудит особенностей и веществ 06.11: находки, часть исправлена — сверять с кодом |
| `perks_proposals_2026-11-06.md` | предложения 06.11; принятое — `PerkConsumers` и `/rpperks audit` |

Дизайн навыков и черт — `RP Stamina Sports/docs/04_skills_and_perks.md`, `05_traits_model.md`.
