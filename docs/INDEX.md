# RP Perks — указатель кода

Создано `python _family/tools/mapgen.py` 15.11.2026, версия 2.11.0-1.20.1-forge, коммит `b3b9c11`.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 29 файлов, 6066 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.rpperks` | 2 | 1041 |
| `faygolover.rpperks.capability` | 2 | 511 |
| `faygolover.rpperks.client` | 2 | 80 |
| `faygolover.rpperks.client.screen` | 1 | 1076 |
| `faygolover.rpperks.commands` | 1 | 83 |
| `faygolover.rpperks.compat` | 2 | 245 |
| `faygolover.rpperks.compat.tacz` | 1 | 117 |
| `faygolover.rpperks.events` | 3 | 1034 |
| `faygolover.rpperks.gametest` | 2 | 164 |
| `faygolover.rpperks.network` | 5 | 399 |
| `faygolover.rpperks.perk` | 4 | 539 |
| `faygolover.rpperks.skill` | 4 | 777 |

## 2. Классы

### `faygolover.rpperks`
- `Config`, 992 — Серверный конфиг мода: world/serverconfig/rpperks-server.toml (для новых миров можно положить готовый файл в defaultconfigs/).
- `Rpperks`, 49 — *без описания*

### `faygolover.rpperks.capability`
- `PlayerPerks`, 455 — Данные перков игрока.
- `PlayerPerksProvider`, 56 — Привязка PlayerPerks к игроку.

### `faygolover.rpperks.client`
- `ClientPacketHandler`, 46 — Обработка пакетов на клиенте.
- `ClientPerkData`, 34 — Клиентские данные, которых нет в capability игрока: чьё окно перков сейчас редактирует админ.

### `faygolover.rpperks.client.screen`
- `PerkScreen`, 1076 — Окно перков.

### `faygolover.rpperks.commands`
- `PerkCommands`, 83 — Всего две команды — всё остальное делается в окне: /rpperks — окно своих перков; /rpperks admin &lt;ник&gt; — админское окно игрока (без ника — своё).

### `faygolover.rpperks.compat`
- `CoreInventory`, 31 — Особенности → инвентарь (RP Inventory через Core).
- `CoreTraits`, 214 — RP Perks для RP Core: навыки семейства и черты по особенностям.

### `faygolover.rpperks.compat.tacz`
- `WeaponSkills`, 117 — Оружейные навыки для TaCZ (грузится, только если TaCZ установлен).

### `faygolover.rpperks.events`
- `PerkActions`, 167 — Серверная обработка кнопок окна перков.
- `PerkEventHandler`, 305 — Жизненный цикл игрока, пересчёт характеристик, синхронизация и наблюдение админа.
- `PerkMechanicsHandler`, 562 — Игровые механики перков: тики, еда, бой, неприятности.

### `faygolover.rpperks.gametest`
- `PerkGameTests`, 23 — Предложения 06.11: у каждой особенности записано, кто даёт ей механику (PerkConsumers).
- `SkillGameTests`, 141 — Навыки 2.7.0 (B1, B2 сводки 02.11): ГМ меняет наработанный уровень и не замораживает навык; старое поле ГМа переводится по правилу 31.10.

### `faygolover.rpperks.network`
- `NetworkHandler`, 54 — *без описания*
- `OpenPerkScreenPacket`, 83 — Открывает окно перков (open = true) или обновляет данные уже открытого админского окна (open = false).
- `PerkActionPacket`, 79 — Нажатие кнопки в окне перков (клиент -> сервер).
- `SyncPerksPacket`, 127 — Снимок данных перков игрока.
- `SyncSkillsPacket` (record), 56 — Навыки игрока для вкладки «Навыки» и чтения уровня на клиенте: ключ навыка → {итоговый уровень, доля опыта до следующего, уровень от перков, скорость роста (талант и особенности)}.

### `faygolover.rpperks.perk`
- `Perk` (enum), 235 — Реестр всех особенностей (перков).
- `PerkConsumers`, 81 — Кто даёт особенности механику (аудит 06.11: у Расторопного, Медлительного и Чувствительности к солнцу кода не было, а описание его обещало).
- `PerkRules`, 75 — Правила самостоятельного выбора перков игроком.
- `Stat` (enum), 148 — Характеристики, которыми управляет мод.

### `faygolover.rpperks.skill`
- `PhysicalEffects`, 91 — Баффы Силы и Телосложения по уровню (RP Stamina Sports docs/04 п.
- `SkillDef` (enum), 62 — Навыки семейства RP, которые ведёт RP Perks (уровни 0–10).
- `SkillEvents`, 89 — События навыков (сервер): ближний бой — любое оружие и голые руки (урон и скорость ударов от уровня, урон от Силы, Силача и Слабака, опыт за удар), Пацифист — урон по людям, раз в минуту — потеря Силы и Телосложения от плохого пит…
- `SkillService`, 535 — Навыки 0–10.

Классов без описания: 2 из 29.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `AttachCapabilitiesEvent` | `PlayerPerksProvider.onAttachCapabilities` |
| `BlockEvent.BreakEvent` | `PerkMechanicsHandler.onBlockBreak` |
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientPerkData.onLoggingOut` |
| `EntityHurtByGunEvent.Post` | `WeaponSkills.onHit` |
| `FMLCommonSetupEvent` | `Rpperks.commonSetup` |
| `GunShootEvent` | `WeaponSkills.onShoot` |
| `LivingChangeTargetEvent` | `PerkMechanicsHandler.onTargetChange` |
| `LivingEntityUseItemEvent.Finish` | `PerkMechanicsHandler.onItemUseFinish` |
| `LivingFallEvent` | `PerkMechanicsHandler.onFall` |
| `LivingHurtEvent` | `SkillEvents.onHurt` |
| `LivingKnockBackEvent` | `PerkMechanicsHandler.onKnockback` |
| `ModConfigEvent.Loading` | `Config.onLoad` |
| `ModConfigEvent.Reloading` | `Config.onReload` |
| `PlayerEvent.Clone` | `PerkEventHandler.onPlayerClone` |
| `PlayerEvent.PlayerChangedDimensionEvent` | `PerkEventHandler.onChangedDimension` |
| `PlayerEvent.PlayerLoggedInEvent` | `PerkEventHandler.onPlayerLogin` |
| `PlayerEvent.PlayerLoggedOutEvent` | `PerkEventHandler.onPlayerLogout`, `SkillEvents.onLogout` |
| `PlayerEvent.PlayerRespawnEvent` | `PerkEventHandler.onPlayerRespawn` |
| `PlayerInteractEvent.RightClickItem` | `PerkMechanicsHandler.onClumsyUse`, `PerkMechanicsHandler.onRightClickFood` |
| `PlayerWakeUpEvent` | `PerkMechanicsHandler.onPlayerWakeUp` |
| `RegisterCapabilitiesEvent` | `Rpperks.registerCapabilities` |
| `RegisterCommandsEvent` | `PerkCommands.onRegisterCommands` |
| `TickEvent.PlayerTickEvent` | `PerkMechanicsHandler.onPlayerTick`, `SkillEvents.onPlayerTick`, `WeaponSkills.onTick` |
| `faygolover.rpcore.api.event.RpCharacterWipeEvent` | `PerkEventHandler.onCharacterWipe` |
| `faygolover.rpcore.api.event.RpConsumeEvent` | `PerkMechanicsHandler.onConsume` |
| `net.minecraftforge.event.entity.living.LivingHealEvent` | `PerkMechanicsHandler.onHeal` |
| `net.minecraftforge.event.entity.living.LivingHurtEvent` | `PerkMechanicsHandler.onHurtTaken` |

## 4. Сеть

Канал: `faygolover/rpperks/network/NetworkHandler.java`, имя `?`, версия протокола `11`.
- `SyncPerksPacket` (направление не указано в регистрации)
- `OpenPerkScreenPacket` (направление не указано в регистрации)
- `SyncSkillsPacket` (направление не указано в регистрации)
- `PerkActionPacket` (направление не указано в регистрации)

Классы с обработчиками пакетов (4): `OpenPerkScreenPacket`, `PerkActionPacket`, `SyncPerksPacket`, `SyncSkillsPacket`.

## 5. Команды

- `faygolover/rpperks/commands/PerkCommands.java`: `rpperks` `audit` `admin`

## 6. Данные

- capability: `PlayerPerksProvider`, ключ `rpperks:player_perks`

## 7. Конфиги

- server: `Config`, вызовов define 101

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `body` 5, `bodyFatPercent` 1, `bodyHandles` 6, `bodyScale` 1, `bodyState` 1, `inventoryChanged` 1, `learningFactor` 1, `nutrition` 3, `nutritionHandles` 2, `profile` 3, `registerCommand` 3, `registerInventoryModifier` 1, `registerModule` 1, `registerProvider` 1, `setProfile` 1, `staminaAttribute` 1, `submitProfile` 1, `submitTaste` 2, `taste` 1, `timeRunning` 1, `weightKg` 3, `wipeCharacter` 1.

Реализует розетки: InventoryModifier ← `CoreInventory`; TraitProvider ← `CoreTraits`.

События Core: `RpCharacterWipeEvent`, `RpConsumeEvent`.

## 9. Связи с другими модами

Проверки `isLoaded`: `rpsports` (SkillService); `tacz` (Rpperks).

Пакеты других модов семейства в коде: `rpcore` — 15 файлов.

## 10. Зарегистрированные id

Нет или регистрируются не строковыми литералами.
