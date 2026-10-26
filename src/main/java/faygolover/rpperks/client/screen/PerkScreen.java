package faygolover.rpperks.client.screen;

import faygolover.rpcore.api.BodyProfile;
import faygolover.rpperks.Config;
import faygolover.rpperks.capability.PlayerPerks;
import faygolover.rpperks.capability.PlayerPerksProvider;
import faygolover.rpperks.client.ClientPerkData;
import faygolover.rpperks.network.NetworkHandler;
import faygolover.rpperks.network.PerkActionPacket;
import faygolover.rpperks.network.PerkActionPacket.Action;
import faygolover.rpperks.perk.Perk;
import faygolover.rpperks.perk.PerkRules;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Окно перков.
 * <ul>
 *   <li>Режим игрока (/rpperks): одинаковый у игрока и ГМа. Пока анкета тела не сохранена — только анкета; потом —
 *   свои перки (первая строка «Взятых» — анкета), каталог, «Взять», «Отказаться» и «Сбросить все» за жетоны.</li>
 *   <li>Режим админа (/rpperks admin &lt;ник&gt;): выдача и снятие любых перков (и служебных), запрет перков, закрытие
 *   выбора, баллы, жетоны, очистка карточки, анкета игрока.</li>
 * </ul>
 * Все действия уходят на сервер пакетом {@link PerkActionPacket}; сервер проверяет их заново.
 */
public class PerkScreen extends Screen {
    // ---------- Размеры (окно растёт с экраном) ----------
    private static final int MIN_W = 380, MAX_W = 480;
    private static final int PAD = 6;
    private static final int ROW = 13;
    private static final int BTN = 16;
    private static final long CONFIRM_MS = 3000;

    // ---------- Цвета (ARGB) ----------
    private static final int C_BORDER = 0xFF4A4F5C;
    private static final int C_BG = 0xF01B1E24;
    private static final int C_PANEL = 0xFF121418;
    private static final int C_HOVER = 0x28FFFFFF;
    private static final int C_SELECTED = 0x55D4A537;
    private static final int C_TITLE = 0xFFE3B341;
    private static final int C_TEXT = 0xFFE6E6E6;
    private static final int C_DIM = 0xFF8C919C;
    private static final int C_GOOD = 0xFF6BCB77;
    private static final int C_BAD = 0xFFE06C6C;
    private static final int C_WARN = 0xFFE5A04B;

    private enum Tab {
        MINE("mine", null),
        MAIN("main", Perk.Category.MAIN),
        SKILL("skill", Perk.Category.SKILL),
        NEGATIVE("negative", Perk.Category.NEGATIVE),
        /** Навыки 0–10: уровень, опыт, откуда уровень. */
        SKILLS("skills", null),
        /** Анкета тела игрока (RP Core) — только в админском окне. */
        PROFILE("profile", null),
        SERVICE("service", Perk.Category.SERVICE);

        final String key;
        @Nullable final Perk.Category category;

        Tab(String key, @Nullable Perk.Category category) {
            this.key = key;
            this.category = category;
        }

        Component title() { return Component.translatable("gui.rpperks.tab." + key); }
    }

    private final boolean adminMode;
    private final UUID targetId;
    private final String targetName;
    private final boolean canSeeHidden;

    private Tab tab = Tab.MINE;
    @Nullable private Perk selected;
    /** Выбрана строка «Анкета» во «Взятых». */
    private boolean profileSelected;
    @Nullable private faygolover.rpperks.skill.SkillDef selectedSkill;
    private static final int SKILL_ROW = 24;
    private double scroll;
    /** Прокрутка описания справа. */
    private double infoScroll;

    private int left, top, panelW, panelHeight, listW;
    private int listX, listY, listH, infoX, infoY, infoW, infoH, bottomY;
    /** С какой разметкой собрано окно: только анкета (своя, ещё не сохранена) или всё. */
    private boolean builtProfileOnly;

    private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
    private Button actionButton;
    @Nullable private Button blockButton;
    @Nullable private Button resetButton;
    @Nullable private Button wipeButton;
    @Nullable private Button selectionButton;

    /** Двойное нажатие для необратимых действий: ключ ожидающего подтверждения и срок. */
    @Nullable private String pendingKey;
    private long pendingUntil;

    // Анкета тела: своя (до сохранения) или игрока в админском окне
    private final List<Button> profileButtons = new ArrayList<>();
    @Nullable private Button sexButton;
    @Nullable private Button saveProfileButton;
    private BodyProfile.Sex profSex = BodyProfile.Sex.NONE;
    private int profAge = BodyProfile.DEFAULT_AGE;
    private int profHeight = (int) BodyProfile.DEFAULT_HEIGHT;
    private boolean profLoaded;
    /** Вкусы (часть анкеты, RP Core): выбор в форме анкеты или отдельной формой «Вкусы…» при уже заполненной анкете. */
    private String tasteFav = "", tasteDis = "";
    private boolean tasteOnly;
    @Nullable private Button favButton, disButton, tasteButton;

    public PerkScreen(boolean adminMode, UUID targetId, String targetName, boolean canSeeHidden) {
        super(adminMode
                ? Component.translatable("gui.rpperks.title.admin", targetName)
                : Component.translatable("gui.rpperks.title.self"));
        this.adminMode = adminMode;
        this.targetId = targetId;
        this.targetName = targetName;
        this.canSeeHidden = adminMode;
    }

    // =====================================================================
    // Данные
    // =====================================================================

    @Nullable
    private PlayerPerks data() {
        if (adminMode) {
            return targetId.equals(ClientPerkData.adminTargetId) ? ClientPerkData.adminTarget : null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.getCapability(PlayerPerksProvider.PLAYER_PERKS).resolve().orElse(null);
    }

    /** Своя анкета (копия с сервера). */
    private BodyProfile ownProfile() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? BodyProfile.DEFAULT : faygolover.rpcore.api.RpCoreAPI.profile(mc.player);
    }

    /** Анкета того, чьё окно: своя или игрока у админа. */
    private BodyProfile shownProfile() {
        return adminMode ? ClientPerkData.targetProfile : ownProfile();
    }

    /** Своё окно, анкета ещё не сохранена: кроме анкеты ничего нет. */
    private boolean profileOnly() {
        return !adminMode && !ownProfile().filled();
    }

    /** Форма анкеты на экране: своя до сохранения или вкладка «Анкета» у админа. */
    private boolean profileForm() {
        return builtProfileOnly || tasteOnly || (adminMode && tab == Tab.PROFILE);
    }

    private List<Tab> visibleTabs() {
        if (builtProfileOnly) return List.of();
        List<Tab> tabs = new ArrayList<>(List.of(Tab.MINE, Tab.MAIN, Tab.SKILL, Tab.NEGATIVE, Tab.SKILLS));
        if (adminMode) {
            tabs.add(Tab.PROFILE);
            tabs.add(Tab.SERVICE);
        }
        return tabs;
    }

    /** Во «Взятых» первая строка — анкета (после сохранения; у админа — всегда). */
    private boolean profileRow() {
        return tab == Tab.MINE && (adminMode || ownProfile().filled());
    }

    private List<Perk> rows(PlayerPerks data) {
        if (tab == Tab.SKILLS || tab == Tab.PROFILE || builtProfileOnly) return List.of();
        if (tab == Tab.MINE) {
            return data.getActivePerks().stream().filter(p -> PerkRules.isVisible(p, canSeeHidden)).toList();
        }
        // В каталоге — по цене (дешёвые сверху), при равной — как в каталоге.
        return Arrays.stream(Perk.values())
                .filter(p -> p.getCategory() == tab.category)
                .filter(p -> PerkRules.isVisible(p, canSeeHidden))
                .sorted(Comparator.comparingInt(Perk::getCost))
                .toList();
    }

    // =====================================================================
    // Разметка и кнопки
    // =====================================================================

    @Override
    protected void init() {
        builtProfileOnly = profileOnly();
        panelW = Mth.clamp(width - 16, MIN_W, MAX_W);
        panelHeight = Mth.clamp(height - 16, adminMode ? 250 : 220, adminMode ? 320 : 300);
        left = (width - panelW) / 2;
        top = (height - panelHeight) / 2;

        int bottomBar = adminMode ? 44 : builtProfileOnly ? 0 : 24;
        bottomY = top + panelHeight - (bottomBar == 0 ? PAD : bottomBar);
        listX = left + PAD;
        listY = top + (builtProfileOnly ? 20 : 36);
        listH = bottomY - (bottomBar == 0 ? 0 : 4) - listY;
        listW = Math.min(200, (panelW - 2 * PAD - 6) * 9 / 20);
        infoX = listX + listW + 6;
        infoY = listY;
        infoW = left + panelW - PAD - infoX;
        infoH = listH;

        // Вкладки
        tabButtons.clear();
        List<Tab> tabs = visibleTabs();
        if (!tabs.isEmpty()) {
            int gap = 2;
            int tabW = (panelW - 2 * PAD - gap * (tabs.size() - 1)) / tabs.size();
            int tx = left + PAD;
            for (Tab t : tabs) {
                Button button = addRenderableWidget(Button.builder(t.title(), b -> selectTab(t))
                        .bounds(tx, top + 18, tabW, 14).build());
                tabButtons.put(t, button);
                tx += tabW + gap;
            }
        }

        // Кнопки в карточке перка. У админа их две: выдать/снять и запретить/разрешить брать
        int buttonY = infoY + infoH - BTN - 4;
        if (adminMode) {
            int half = (infoW - 10) / 2;
            actionButton = addRenderableWidget(Button.builder(Component.empty(), b -> onAction())
                    .bounds(infoX + 4, buttonY, half, BTN).build());
            blockButton = addRenderableWidget(Button.builder(Component.empty(), b -> onToggleBlock())
                    .bounds(infoX + 6 + half, buttonY, half, BTN).build());
        } else {
            actionButton = addRenderableWidget(Button.builder(Component.empty(), b -> onAction())
                    .bounds(infoX + 4, buttonY, infoW - 8, BTN).build());
        }

        // Анкета тела: кнопки в левой панели
        profileButtons.clear();
        sexButton = null;
        saveProfileButton = null;
        favButton = disButton = null;
        if (builtProfileOnly || adminMode || tasteOnly) {
            int bx = listX + listW - 6;
            int py = listY + 8;
            if (!adminMode && (builtProfileOnly || tasteOnly)) {
                int ty = tasteOnly ? py : py + 66;
                favButton = profileButton(Component.empty(), bx - 110, ty, 110, () -> tasteFav = nextTaste(tasteFav));
                disButton = profileButton(Component.empty(), bx - 110, ty + 22, 110, () -> tasteDis = nextTaste(tasteDis));
            }
        }
        if (tasteOnly) {
            saveProfileButton = profileButton(Component.empty(), listX + 6, listY + 8 + 50, listW - 12, this::onSaveProfile);
        } else if (builtProfileOnly || adminMode) {
            int bx = listX + listW - 6;
            int py = listY + 8;
            sexButton = profileButton(Component.empty(), bx - 90, py, 90, () -> profSex = nextSex(profSex));
            profileButton(Component.literal("-5"), bx - 90, py + 22, 20, () -> profAge -= 5);
            profileButton(Component.literal("-1"), bx - 68, py + 22, 20, () -> profAge -= 1);
            profileButton(Component.literal("+1"), bx - 42, py + 22, 20, () -> profAge += 1);
            profileButton(Component.literal("+5"), bx - 20, py + 22, 20, () -> profAge += 5);
            profileButton(Component.literal("-5"), bx - 90, py + 44, 20, () -> profHeight -= 5);
            profileButton(Component.literal("-1"), bx - 68, py + 44, 20, () -> profHeight -= 1);
            profileButton(Component.literal("+1"), bx - 42, py + 44, 20, () -> profHeight += 1);
            profileButton(Component.literal("+5"), bx - 20, py + 44, 20, () -> profHeight += 5);
            saveProfileButton = profileButton(Component.empty(), listX + 6, adminMode ? py + 72 : py + 116, listW - 12, this::onSaveProfile);
            if (builtProfileOnly && !profLoaded) loadProfile(ownProfile());
        }

        resetButton = null;
        wipeButton = null;
        selectionButton = null;
        tasteButton = null;
        int by = bottomY + 4;
        if (!adminMode) {
            if (!builtProfileOnly) {
                resetButton = addRenderableWidget(Button.builder(Component.empty(), b -> onReset())
                        .bounds(left + PAD, by, 140, BTN).build());
                // Вкусы ещё не выбраны — отдельная форма (анкета уже заполнена).
                if (tasteOnly || !ownTaste().chosen()) {
                    tasteButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleTaste())
                            .bounds(left + panelW - PAD - 100, by, 100, BTN).build());
                }
            }
        } else {
            // Ряд 1: баллы и очистка карточки
            small("-10", left + 58, by, 26, () -> admin(Action.ADMIN_POINTS, null, -10));
            small("-1", left + 86, by, 22, () -> admin(Action.ADMIN_POINTS, null, -1));
            small("+1", left + 152, by, 22, () -> admin(Action.ADMIN_POINTS, null, 1));
            small("+10", left + 176, by, 26, () -> admin(Action.ADMIN_POINTS, null, 10));
            wipeButton = addRenderableWidget(Button.builder(Component.empty(), b -> onWipe())
                    .bounds(left + panelW - PAD - 120, by, 120, BTN).build());

            // Ряд 2: жетоны
            int by2 = by + 20;
            small("-", left + 58, by2, 20, () -> admin(Action.ADMIN_REFUSE_TOKENS, null, -1));
            small("+", left + 108, by2, 20, () -> admin(Action.ADMIN_REFUSE_TOKENS, null, 1));
            small("-", left + 176, by2, 20, () -> admin(Action.ADMIN_RESET_TOKENS, null, -1));
            small("+", left + 226, by2, 20, () -> admin(Action.ADMIN_RESET_TOKENS, null, 1));
            selectionButton = addRenderableWidget(Button.builder(Component.empty(),
                            b -> admin(Action.ADMIN_TOGGLE_SELECTION, null, 0))
                    .bounds(left + panelW - PAD - 120, by2, 120, BTN).build());
        }
        updateButtons();
    }

    private faygolover.rpcore.api.FoodTaste ownTaste() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player == null ? faygolover.rpcore.api.FoodTaste.NONE : faygolover.rpcore.api.RpCoreAPI.taste(mc.player);
    }

    /** Следующий вариант вкуса по кругу; первый — «не выбрано». */
    private static String nextTaste(String v) {
        List<String> opts = faygolover.rpcore.api.FoodTaste.options();
        int i = opts.indexOf(v);
        return i + 1 >= opts.size() ? "" : opts.get(i + 1);
    }

    private void toggleTaste() {
        tasteOnly = !tasteOnly;
        tasteFav = tasteDis = "";
        pendingKey = null;
        rebuildWidgets();
    }

    private Button profileButton(Component label, int x, int y, int w, Runnable action) {
        Button b = addRenderableWidget(Button.builder(label, btn -> {
            action.run();
            clampProfile();
            if (btn != saveProfileButton) pendingKey = null;
            updateButtons();
        }).bounds(x, y, w, BTN).build());
        profileButtons.add(b);
        return b;
    }

    private static BodyProfile.Sex nextSex(BodyProfile.Sex s) {
        return switch (s) {
            case NONE -> BodyProfile.Sex.MALE;
            case MALE -> BodyProfile.Sex.FEMALE;
            case FEMALE -> BodyProfile.Sex.NONE;
        };
    }

    private void clampProfile() {
        profAge = Mth.clamp(profAge, BodyProfile.MIN_AGE, BodyProfile.MAX_AGE);
        profHeight = Mth.clamp(profHeight, (int) BodyProfile.MIN_HEIGHT, (int) BodyProfile.MAX_HEIGHT);
    }

    private void loadProfile(BodyProfile prof) {
        profSex = prof.sex();
        profAge = prof.age();
        profHeight = (int) Math.round(prof.heightCm());
        profLoaded = true;
    }

    private void onSaveProfile() {
        if (adminMode) {
            if (!confirm("profile")) return;
            send(Action.ADMIN_PROFILE, null, Action.packProfile(profSex.ordinal(), profAge, profHeight));
            playClick();
            return;
        }
        if (!faygolover.rpcore.api.FoodTaste.validPair(tasteFav, tasteDis)) return;
        if (tasteOnly) {
            if (tasteFav.isEmpty() || !confirm("taste")) return;
            faygolover.rpcore.api.RpCoreAPI.submitTaste(tasteFav, tasteDis);
            playClick();
            tasteOnly = false;
            rebuildWidgets();
            return;
        }
        if (ownProfile().filled() || !confirm("profile")) return;
        faygolover.rpcore.api.RpCoreAPI.submitProfile(profSex, profAge, profHeight);
        // Вкусы необязательны: не выбраны — выберет позже кнопкой «Вкусы…».
        if (!tasteFav.isEmpty()) faygolover.rpcore.api.RpCoreAPI.submitTaste(tasteFav, tasteDis);
        playClick();
    }

    private void small(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, BTN).build());
    }

    private void selectTab(Tab t) {
        tab = t;
        if (t == Tab.PROFILE) loadProfile(shownProfile());
        scroll = 0;
        infoScroll = 0;
        profileSelected = false;
        pendingKey = null;
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingKey != null && Util.getMillis() > pendingUntil) pendingKey = null;
        // Анкету сохранили (пришла с сервера) — открыть остальное окно.
        if (builtProfileOnly != profileOnly()) {
            tab = Tab.MINE;
            rebuildWidgets();
            return;
        }
        updateButtons();
    }

    private void updateButtons() {
        tabButtons.forEach((t, b) -> b.active = t != tab);
        PlayerPerks data = data();
        boolean form = profileForm();
        for (Button b : profileButtons) b.visible = form;
        if (sexButton != null) sexButton.setMessage(Component.translatable("rpcore.profile.sex." + profSex.id()));
        if (favButton != null) favButton.setMessage(Component.translatable(faygolover.rpcore.api.FoodTaste.langKey(tasteFav)));
        if (disButton != null) disButton.setMessage(Component.translatable(faygolover.rpcore.api.FoodTaste.langKey(tasteDis)));
        if (tasteButton != null) tasteButton.setMessage(Component.translatable(tasteOnly ? "gui.rpperks.taste.back" : "gui.rpperks.taste.button"));
        if (saveProfileButton != null) {
            boolean pairOk = adminMode || faygolover.rpcore.api.FoodTaste.validPair(tasteFav, tasteDis);
            saveProfileButton.active = pairOk && (!tasteOnly || !tasteFav.isEmpty());
            saveProfileButton.setMessage(isPending(tasteOnly ? "taste" : "profile")
                    ? Component.translatable("gui.rpperks.button.confirm")
                    : !pairOk ? Component.translatable("gui.rpperks.taste.need_pair")
                    : Component.translatable(adminMode ? "gui.rpperks.profile.admin_save"
                            : tasteOnly ? "gui.rpperks.taste.save" : "gui.rpperks.profile.save"));
        }

        if (blockButton != null) {
            blockButton.visible = data != null && selected != null && !profileSelected && selected.getCategory() != Perk.Category.SERVICE;
            if (blockButton.visible) {
                blockButton.setMessage(Component.translatable(data.isBlocked(selected)
                        ? "gui.rpperks.button.unblock" : "gui.rpperks.button.block"));
            }
        }
        if (selectionButton != null) {
            selectionButton.active = data != null;
            selectionButton.setMessage(Component.translatable(data != null && data.isSelectionLocked()
                    ? "gui.rpperks.button.selection_closed" : "gui.rpperks.button.selection_open"));
        }

        if (form || profileSelected) {
            actionButton.visible = false;
            if (blockButton != null) blockButton.visible = false;
        } else if (tab == Tab.SKILLS) {
            // У админа на вкладке навыков одна кнопка: снять уровень, выставленный ГМом
            if (blockButton != null) blockButton.visible = false;
            float[] v = data != null && selectedSkill != null ? data.getClientSkills().get(selectedSkill.key()) : null;
            actionButton.visible = adminMode && v != null && (int) v[2] == 2;
            if (actionButton.visible) {
                actionButton.active = true;
                actionButton.setWidth(infoW - 8);
                actionButton.setMessage(Component.translatable("gui.rpperks.button.skill_reset"));
            }
        } else if (data == null || selected == null) {
            actionButton.visible = false;
        } else {
            if (adminMode) actionButton.setWidth((infoW - 10) / 2);
            actionButton.visible = true;
            boolean owned = data.owns(selected);
            if (adminMode) {
                actionButton.active = true;
                actionButton.setMessage(Component.translatable(owned ? "gui.rpperks.button.revoke" : "gui.rpperks.button.grant"));
            } else {
                Component reason = owned
                        ? PerkRules.whyCannotRefuse(data, selected)
                        : PerkRules.whyCannotTake(data, selected, canSeeHidden);
                actionButton.active = reason == null;
                String key = actionKey(owned, selected);
                actionButton.setMessage(isPending(key)
                        ? Component.translatable("gui.rpperks.button.confirm")
                        : Component.translatable(owned ? "gui.rpperks.button.refuse" : "gui.rpperks.button.take"));
            }
        }

        if (resetButton != null) {
            resetButton.active = data != null && PerkRules.whyCannotReset(data) == null;
            resetButton.setMessage(isPending("reset")
                    ? Component.translatable("gui.rpperks.button.confirm")
                    : Component.translatable("gui.rpperks.button.reset"));
        }
        if (wipeButton != null) {
            wipeButton.active = data != null;
            wipeButton.setMessage(isPending("wipe")
                    ? Component.translatable("gui.rpperks.button.confirm")
                    : Component.translatable("gui.rpperks.button.wipe"));
        }
    }

    // =====================================================================
    // Действия
    // =====================================================================

    private static String actionKey(boolean owned, Perk perk) {
        return (owned ? "refuse:" : "take:") + perk.getId();
    }

    private boolean isPending(String key) {
        return key.equals(pendingKey) && Util.getMillis() <= pendingUntil;
    }

    /** Первое нажатие просит подтверждения, второе (в течение 3 секунд) выполняет. */
    private boolean confirm(String key) {
        if (isPending(key)) {
            pendingKey = null;
            return true;
        }
        pendingKey = key;
        pendingUntil = Util.getMillis() + CONFIRM_MS;
        updateButtons();
        return false;
    }

    private void onAction() {
        if (tab == Tab.SKILLS) {
            if (adminMode && selectedSkill != null) admin(Action.ADMIN_SKILL_RESET, null, selectedSkill.ordinal());
            return;
        }
        PlayerPerks data = data();
        if (data == null || selected == null || profileSelected) return;
        boolean owned = data.owns(selected);
        if (adminMode) {
            admin(owned ? Action.ADMIN_REVOKE : Action.ADMIN_GRANT, selected, 0);
            return;
        }
        if (!confirm(actionKey(owned, selected))) return;
        send(owned ? Action.REFUSE : Action.TAKE, selected, 0);
    }

    private void onToggleBlock() {
        if (selected != null) admin(Action.ADMIN_TOGGLE_BLOCK, selected, 0);
    }

    private void onReset() {
        if (!confirm("reset")) return;
        send(Action.RESET, null, 0);
    }

    private void onWipe() {
        if (!confirm("wipe")) return;
        admin(Action.ADMIN_WIPE, null, 0);
    }

    private void admin(Action action, @Nullable Perk perk, int amount) {
        send(action, perk, amount);
    }

    private void send(Action action, @Nullable Perk perk, int amount) {
        NetworkHandler.sendToServer(new PerkActionPacket(action, targetId, perk, amount));
        updateButtons();
    }

    // =====================================================================
    // Мышь
    // =====================================================================

    private boolean inList(double mx, double my) {
        return mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;
    }

    private boolean inInfo(double mx, double my) {
        return mx >= infoX && mx < infoX + infoW && my >= infoY && my < infoY + infoH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (profileForm()) return false;
        if (button == 0 && inList(mx, my) && tab == Tab.SKILLS) {
            int index = (int) Math.floor((my - listY - 2 + scroll) / SKILL_ROW);
            if (index >= 0 && index < faygolover.rpperks.skill.SkillDef.VALUES.length) {
                var skill = faygolover.rpperks.skill.SkillDef.VALUES[index];
                if (adminMode) {
                    int rowY = listY + 2 - (int) scroll + index * SKILL_ROW;
                    if (my >= rowY + 1 && my < rowY + 12) {
                        if (mx >= plusX() && mx < plusX() + SKILL_BTN) {
                            admin(Action.ADMIN_SKILL_UP, null, skill.ordinal());
                            playClick();
                            return true;
                        }
                        if (mx >= minusX() && mx < minusX() + SKILL_BTN) {
                            admin(Action.ADMIN_SKILL_DOWN, null, skill.ordinal());
                            playClick();
                            return true;
                        }
                    }
                }
                selectedSkill = skill;
                infoScroll = 0;
                updateButtons();
                return true;
            }
            return false;
        }
        if (button == 0 && inList(mx, my)) {
            PlayerPerks data = data();
            if (data == null) return false;
            List<Perk> rows = rows(data);
            int index = (int) Math.floor((my - listY - 2 + scroll) / ROW);
            if (profileRow()) {
                if (index == 0) {
                    profileSelected = true;
                    selected = null;
                    infoScroll = 0;
                    pendingKey = null;
                    updateButtons();
                    return true;
                }
                index--;
            }
            if (index >= 0 && index < rows.size()) {
                selected = rows.get(index);
                profileSelected = false;
                infoScroll = 0;
                pendingKey = null;
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inList(mx, my)) {
            scroll -= delta * ROW * 2;
            return true;
        }
        if (inInfo(mx, my)) {
            infoScroll -= delta * 20;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================
    // Отрисовка
    // =====================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // Рамка, фон и панели
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelHeight + 1, C_BORDER);
        g.fill(left, top, left + panelW, top + panelHeight, C_BG);
        g.fill(listX, listY, listX + listW, listY + listH, C_PANEL);
        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, C_PANEL);
        if (!builtProfileOnly) g.fill(left + PAD, bottomY, left + panelW - PAD, bottomY + 1, C_BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, title, left + PAD, top + 6, C_TITLE, false);

        PlayerPerks data = data();
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.rpperks.no_data"), left + panelW / 2, listY + 20, C_DIM);
            return;
        }

        if (!builtProfileOnly) renderStats(g, data);
        renderList(g, data, mouseX, mouseY);
        renderInfo(g, data);
        renderBottom(g, data);
    }

    private void renderStats(GuiGraphics g, PlayerPerks data) {
        String pointsColor = data.getPoints() < 0 ? "§c" : "§e";
        Component stats = Component.translatable("gui.rpperks.stats",
                pointsColor + data.getPoints(), data.getRefuseTokens(), data.getResetTokens());
        g.drawString(font, stats, left + panelW - PAD - font.width(stats), top + 6, C_TEXT, false);
    }

    private void renderList(GuiGraphics g, PlayerPerks data, int mouseX, int mouseY) {
        if (profileForm()) {
            int py = listY + 8;
            if (favButton != null) {
                int ty = tasteOnly ? py : py + 66;
                g.drawString(font, Component.translatable("gui.rpperks.taste.favourite"), listX + 6, ty + 4, C_TEXT, false);
                g.drawString(font, Component.translatable("gui.rpperks.taste.disliked"), listX + 6, ty + 26, C_TEXT, false);
            }
            if (tasteOnly) return;
            g.drawString(font, Component.translatable("gui.rpperks.profile.sex"), listX + 6, py + 4, C_TEXT, false);
            g.drawString(font, Component.translatable("gui.rpperks.profile.age"), listX + 6, py + 26, C_TEXT, false);
            g.drawString(font, String.valueOf(profAge), listX + 52, py + 26, C_TITLE, false);
            g.drawString(font, Component.translatable("gui.rpperks.profile.height"), listX + 6, py + 48, C_TEXT, false);
            g.drawString(font, profHeight + "", listX + 52, py + 48, C_TITLE, false);
            return;
        }
        if (tab == Tab.SKILLS) {
            renderSkills(g, data, mouseX, mouseY);
            return;
        }
        List<Perk> rows = rows(data);
        boolean profRow = profileRow();
        int count = rows.size() + (profRow ? 1 : 0);
        int contentH = count * ROW + 4;
        int maxScroll = Math.max(0, contentH - listH);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        if (count == 0) {
            g.drawCenteredString(font, Component.translatable("gui.rpperks.empty"), listX + listW / 2, listY + 12, C_DIM);
            return;
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + 2 - (int) scroll;
        if (profRow) {
            boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
            if (profileSelected) g.fill(listX + 1, y, listX + listW - 1, y + ROW, C_SELECTED);
            else if (hover) g.fill(listX + 1, y, listX + listW - 1, y + ROW, C_HOVER);
            g.drawString(font, "§6" + Component.translatable("gui.rpperks.profile.row").getString(), listX + 4, y + 3, C_TEXT, false);
            y += ROW;
        }
        for (Perk perk : rows) {
            if (y + ROW > listY && y < listY + listH) {
                boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
                if (perk == selected && !profileSelected) g.fill(listX + 1, y, listX + listW - 1, y + ROW, C_SELECTED);
                else if (hover) g.fill(listX + 1, y, listX + listW - 1, y + ROW, C_HOVER);

                boolean owned = data.owns(perk);
                boolean available = owned || adminMode || PerkRules.whyCannotTake(data, perk, canSeeHidden) == null;

                String cost = costLabel(perk);
                int costX = listX + listW - 8 - font.width(cost);

                String name = Component.translatable(perk.getNameKey()).getString();
                if (!available) name = "§8" + ChatFormatting.stripFormatting(name);
                String prefix = owned ? "§a✔ " : data.isBlocked(perk) ? "§c✖ " : "  ";
                String line = font.plainSubstrByWidth(prefix + name, costX - listX - 8);

                g.drawString(font, line, listX + 4, y + 3, C_TEXT, false);
                g.drawString(font, cost, costX, y + 3, C_TEXT, false);
            }
            y += ROW;
        }
        g.disableScissor();

        // Полоса прокрутки
        if (maxScroll > 0) {
            int barH = Math.max(12, listH * listH / contentH);
            int barY = listY + (int) ((listH - barH) * (scroll / maxScroll));
            g.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, C_DIM);
        }
    }

    private static String costLabel(Perk perk) {
        return perk.isPositive() ? "§a-" + perk.getCost() : "§c+" + perk.getCost();
    }

    /** Вкладка «Навыки»: название, уровень, полоска опыта до следующего уровня. */
    private void renderSkills(GuiGraphics g, PlayerPerks data, int mouseX, int mouseY) {
        var skills = faygolover.rpperks.skill.SkillDef.VALUES;
        int contentH = skills.length * SKILL_ROW + 4;
        int maxScroll = Math.max(0, contentH - listH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY + 2 - (int) scroll;
        for (var s : skills) {
            float[] v = data.getClientSkills().get(s.key());
            int lvl = v != null ? (int) v[0] : 0;
            float frac = v != null ? v[1] : 0f;
            boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + SKILL_ROW;
            if (s == selectedSkill) g.fill(listX + 1, y, listX + listW - 1, y + SKILL_ROW, C_SELECTED);
            else if (hover) g.fill(listX + 1, y, listX + listW - 1, y + SKILL_ROW, C_HOVER);
            g.drawString(font, Component.translatable(s.nameKey()), listX + 4, y + 3, C_TEXT, false);
            String l = String.valueOf(lvl);
            int lvlColor = lvl >= 7 ? C_GOOD : lvl <= 1 ? C_BAD : C_TITLE;
            if (adminMode) {
                // [−] уровень [+]
                skillButton(g, minusX(), y + 1, "-", mouseX, mouseY);
                skillButton(g, plusX(), y + 1, "+", mouseX, mouseY);
                int mid = (minusX() + SKILL_BTN + plusX()) / 2;
                g.drawString(font, l, mid - font.width(l) / 2, y + 3, lvlColor, false);
            } else {
                g.drawString(font, l, listX + listW - 8 - font.width(l), y + 3, lvlColor, false);
            }
            int bx = listX + 4, bw = listW - 12, by = y + 15;
            g.fill(bx, by, bx + bw, by + 4, 0xFF2A2E36);
            int filled = (int) (bw * Math.min(10, lvl + (lvl >= 10 ? 0 : frac)) / 10f);
            g.fill(bx, by, bx + filled, by + 4, 0xFFD4A537);
            // Деления поверх заливки — видны и на заполненной части
            for (int i = 1; i < 10; i++) {
                int tx = bx + bw * i / 10;
                g.fill(tx, by, tx + 1, by + 4, tx < bx + filled ? 0xFF7A5A12 : 0xFF1B1E24);
            }
            y += SKILL_ROW;
        }
        g.disableScissor();
    }

    private static final int SKILL_BTN = 11;

    private int plusX() {
        return listX + listW - 6 - SKILL_BTN;
    }

    private int minusX() {
        return plusX() - SKILL_BTN - 16;
    }

    private void skillButton(GuiGraphics g, int x, int y, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + SKILL_BTN && mouseY >= y && mouseY < y + SKILL_BTN;
        g.fill(x, y, x + SKILL_BTN, y + SKILL_BTN, C_BORDER);
        g.fill(x + 1, y + 1, x + SKILL_BTN - 1, y + SKILL_BTN - 1, hover ? 0xFF3A3F4A : 0xFF23262D);
        g.drawString(font, label, x + (SKILL_BTN - font.width(label)) / 2 + 1, y + 2, hover ? C_TITLE : C_TEXT, false);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---------- Описание справа: строки с прокруткой ----------

    private record Line(FormattedCharSequence text, int color, int height) {}

    private final List<Line> content = new ArrayList<>();

    private void text(Component c, int color, int w) {
        for (FormattedCharSequence line : font.split(c, w)) content.add(new Line(line, color, 10));
    }

    private void line(Component c, int color, int height) {
        content.add(new Line(c.getVisualOrderText(), color, height));
    }

    private void gap(int h) {
        content.add(new Line(FormattedCharSequence.EMPTY, 0, h));
    }

    /** Нарисовать собранные строки между {@code y0} и {@code bottom}; не влезают — прокрутка колесом. */
    private void drawContent(GuiGraphics g, int x, int y0, int bottom) {
        int total = 0;
        for (Line l : content) total += l.height;
        int max = Math.max(0, total - (bottom - y0));
        infoScroll = Mth.clamp(infoScroll, 0, max);
        g.enableScissor(infoX, y0, infoX + infoW, bottom);
        int y = y0 - (int) infoScroll;
        for (Line l : content) {
            if (y + l.height > y0 && y < bottom) g.drawString(font, l.text, x, y, l.color, false);
            y += l.height;
        }
        g.disableScissor();
        if (max > 0) {
            int h = bottom - y0;
            int barH = Math.max(10, h * h / total);
            int barY = y0 + (int) ((h - barH) * (infoScroll / max));
            g.fill(infoX + infoW - 3, barY, infoX + infoW - 1, barY + barH, C_DIM);
        }
        content.clear();
    }

    private void renderSkillInfo(PlayerPerks data, int w) {
        if (selectedSkill == null) {
            text(Component.translatable("gui.rpperks.skills.hint"), C_DIM, w);
            if (adminMode) {
                gap(4);
                text(Component.translatable("gui.rpperks.skills.admin_hint"), C_WARN, w);
            }
            if (!faygolover.rpperks.Config.get(faygolover.rpperks.Config.SKILL_GROWTH)) {
                gap(4);
                text(Component.translatable("gui.rpperks.skills.growth_off"), C_WARN, w);
            }
            return;
        }
        var s = selectedSkill;
        float[] v = data.getClientSkills().get(s.key());
        int lvl = v != null ? (int) v[0] : 0;
        line(Component.translatable(s.nameKey()), C_TITLE, 12);
        String src = v == null ? "trained" : switch ((int) v[2]) { case 1 -> "perk"; case 2 -> "gm"; default -> "trained"; };
        line(Component.translatable("gui.rpperks.skills.level", lvl).append(" · ")
                .append(Component.translatable("gui.rpperks.skills.source." + src)), C_TEXT, 12);
        if (v != null && lvl < 10) line(Component.literal((int) (v[1] * 100) + "%"), C_DIM, 12);
        else if (lvl >= 10) line(Component.translatable("gui.rpperks.skills.max"), C_GOOD, 12);
        gap(4);
        text(Component.translatable(s.descKey()), C_DIM, w);
    }

    /** Анкета (строка во «Взятых»): значения и текущий вес. */
    private void renderProfileInfo(int w) {
        BodyProfile p = shownProfile();
        line(Component.translatable("gui.rpperks.profile.row"), C_TITLE, 14);
        line(Component.translatable("gui.rpperks.profile.sex_label", Component.translatable("rpcore.profile.sex." + p.sex().id())), C_TEXT, 12);
        line(Component.translatable("gui.rpperks.profile.age_label", p.age()), C_TEXT, 12);
        line(Component.translatable("gui.rpperks.profile.height_label", (int) Math.round(p.heightCm())), C_TEXT, 12);
        double kg = ClientPerkData.targetWeightKg;
        if (Double.isFinite(kg)) {
            line(Component.translatable("gui.rpperks.profile.weight_label", Component.translatable("gui.rpperks.profile.weight_value",
                    String.format(java.util.Locale.ROOT, "%.1f", kg))), C_TEXT, 12);
        }
        if (!adminMode) {
            faygolover.rpcore.api.FoodTaste t = ownTaste();
            if (t.chosen()) {
                line(Component.translatable("gui.rpperks.taste.fav_label", Component.translatable(faygolover.rpcore.api.FoodTaste.langKey(t.favourite()))), C_TEXT, 12);
                line(Component.translatable("gui.rpperks.taste.dis_label", Component.translatable(faygolover.rpcore.api.FoodTaste.langKey(t.disliked()))), C_TEXT, 12);
            }
            gap(6);
            text(Component.translatable("gui.rpperks.profile.gm_only"), C_DIM, w);
        }
    }

    private void renderInfo(GuiGraphics g, PlayerPerks data) {
        int x = infoX + 6;
        int w = infoW - 12;
        int y0 = infoY + 6;
        int bottom = infoY + infoH - 4;
        if (profileForm()) {
            if (tasteOnly) {
                text(Component.translatable("gui.rpperks.taste.hint"), C_TEXT, w);
                drawContent(g, x, y0, bottom);
                return;
            }
            text(Component.translatable(builtProfileOnly ? "gui.rpperks.profile.first" : "gui.rpperks.profile.admin_hint"), C_TEXT, w);
            gap(6);
            text(Component.translatable("gui.rpperks.profile.hint"), C_DIM, w);
            if (builtProfileOnly) {
                gap(6);
                text(Component.translatable("gui.rpperks.taste.hint"), C_DIM, w);
            }
            drawContent(g, x, y0, bottom);
            return;
        }
        if (tab == Tab.SKILLS) {
            renderSkillInfo(data, w);
            drawContent(g, x, y0, actionButton.visible ? actionButton.getY() - 2 : bottom);
            return;
        }
        if (profileSelected) {
            renderProfileInfo(w);
            drawContent(g, x, y0, bottom);
            return;
        }
        if (selected == null) {
            text(Component.translatable(adminMode ? "gui.rpperks.select_hint_admin" : "gui.rpperks.select_hint"), C_DIM, w);
            drawContent(g, x, y0, bottom);
            return;
        }
        Perk perk = selected;
        boolean owned = data.owns(perk);

        // Название, цена, описание, несовместимости — прокручиваются
        line(Component.translatable(perk.getNameKey()), C_TEXT, 12);
        Component tabName = Component.translatable("gui.rpperks.tab." + perk.getCategory().getId());
        line(Component.translatable(perk.isPositive() ? "gui.rpperks.cost.positive" : "gui.rpperks.cost.negative",
                tabName, perk.getCost()), C_DIM, 14);
        text(Component.translatable(perk.getDescKey()), C_TEXT, w);
        Set<Perk> conflicts = Config.getConflicts(perk);
        if (!conflicts.isEmpty()) {
            gap(4);
            text(Component.translatable("gui.rpperks.conflicts", joinNames(conflicts)), C_WARN, w);
        }

        // Статус над кнопкой — на месте
        List<Status> status = new ArrayList<>();
        if (owned && perk == Perk.CAFFEINE_ADDICTION && !data.isAdmin()) {
            int remaining = Config.caffeineWithdrawalTicks() - data.getCaffeineTimer();
            status.add(remaining > 0
                    ? new Status(Component.translatable("gui.rpperks.caffeine_left", (remaining + 1199) / 1200), C_DIM)
                    : new Status(Component.translatable("gui.rpperks.caffeine_crash"), C_BAD));
        }
        if (adminMode && data.isBlocked(perk)) {
            status.add(new Status(Component.translatable("gui.rpperks.blocked_admin"), C_BAD));
        }
        if (owned) {
            status.add(new Status(Component.translatable(adminMode ? "gui.rpperks.owned_admin" : "gui.rpperks.owned"), C_GOOD));
            if (!adminMode) {
                Component reason = PerkRules.whyCannotRefuse(data, perk);
                if (reason != null) status.add(new Status(reason, C_BAD));
            }
        } else if (adminMode) {
            List<Perk> willRemove = conflicts.stream().filter(data::owns).toList();
            if (!willRemove.isEmpty()) {
                status.add(new Status(Component.translatable("gui.rpperks.will_remove", joinNames(willRemove)), C_WARN));
            }
        } else {
            Component reason = PerkRules.whyCannotTake(data, perk, canSeeHidden);
            status.add(reason == null
                    ? new Status(Component.translatable("gui.rpperks.permanent"), C_WARN)
                    : new Status(reason, C_BAD));
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Status s : status) {
            for (FormattedCharSequence l : font.split(s.text, w)) {
                lines.add(l);
                colors.add(s.color);
            }
        }
        int sy = infoY + infoH - BTN - 8 - lines.size() * 10;
        drawContent(g, x, y0, Math.max(y0 + 20, sy - 4));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, sy + i * 10, colors.get(i), false);
        }
    }

    private record Status(Component text, int color) {}

    private void renderBottom(GuiGraphics g, PlayerPerks data) {
        if (builtProfileOnly) return;
        int by = bottomY + 4;
        if (!adminMode) {
            int hx = left + PAD + 140 + 8;
            int hy = by - 1;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.rpperks.hint"), left + panelW - PAD - hx)) {
                g.drawString(font, line, hx, hy, C_DIM, false);
                hy += 9;
            }
            return;
        }
        int textY = by + 4;
        g.drawString(font, Component.translatable("gui.rpperks.label.points"), left + PAD, textY, C_TEXT, false);
        drawCentered(g, (data.getPoints() < 0 ? "§c" : "§e") + data.getPoints(), left + 130, textY);

        int by2 = by + 20 + 4;
        g.drawString(font, Component.translatable("gui.rpperks.label.refuse"), left + PAD, by2, C_TEXT, false);
        drawCentered(g, "§f" + data.getRefuseTokens(), left + 93, by2);
        g.drawString(font, Component.translatable("gui.rpperks.label.reset"), left + 136, by2, C_TEXT, false);
        drawCentered(g, "§f" + data.getResetTokens(), left + 211, by2);
    }

    private void drawCentered(GuiGraphics g, String text, int centerX, int y) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, C_TEXT, false);
    }

    private static Component joinNames(Collection<Perk> perks) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (Perk perk : perks) {
            if (!first) result.append(Component.literal("§7, "));
            result.append(Component.translatable(perk.getNameKey()));
            first = false;
        }
        return result;
    }
}
