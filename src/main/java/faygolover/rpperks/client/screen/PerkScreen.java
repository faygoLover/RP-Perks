package faygolover.rpperks.client.screen;

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
 *   <li>Режим игрока (/perks): свои перки, каталог, «Взять», «Отказаться» и «Сбросить все» за жетоны.</li>
 *   <li>Режим админа (/perksadmin &lt;ник&gt;): выдача и снятие любых перков, запрет перков, закрытие выбора,
 *   баллы, жетоны, очистка карточки.</li>
 * </ul>
 * Все действия уходят на сервер пакетом {@link PerkActionPacket}; сервер проверяет их заново.
 */
public class PerkScreen extends Screen {
    // ---------- Размеры ----------
    private static final int WIDTH = 380;
    private static final int HEIGHT_SELF = 236;
    private static final int HEIGHT_ADMIN = 258;
    private static final int PAD = 8;
    private static final int ROW = 13;
    private static final int LIST_W = 186;
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
        SERVICE("service", Perk.Category.SERVICE),
        /** Навыки 0–10: уровень, опыт, откуда уровень (свои данные игрока). */
        SKILLS("skills", null);

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
    @Nullable private faygolover.rpperks.skill.SkillDef selectedSkill;
    private static final int SKILL_ROW = 24;
    private double scroll;

    private int left, top, panelHeight;
    private int listX, listY, listH, infoX, infoY, infoW, infoH, bottomY;

    private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
    private Button actionButton;
    @Nullable private Button blockButton;
    @Nullable private Button resetButton;
    @Nullable private Button wipeButton;
    @Nullable private Button selectionButton;

    /** Двойное нажатие для необратимых действий: ключ ожидающего подтверждения и срок. */
    @Nullable private String pendingKey;
    private long pendingUntil;

    public PerkScreen(boolean adminMode, UUID targetId, String targetName, boolean canSeeHidden) {
        super(adminMode
                ? Component.translatable("gui.rpperks.title.admin", targetName)
                : Component.translatable("gui.rpperks.title.self"));
        this.adminMode = adminMode;
        this.targetId = targetId;
        this.targetName = targetName;
        this.canSeeHidden = canSeeHidden || adminMode;
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

    private List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>(List.of(Tab.MINE, Tab.MAIN, Tab.SKILL, Tab.NEGATIVE));
        if (!adminMode) tabs.add(Tab.SKILLS);
        if (canSeeHidden) tabs.add(Tab.SERVICE);
        return tabs;
    }

    private List<Perk> rows(PlayerPerks data) {
        if (tab == Tab.SKILLS) return List.of();
        if (tab == Tab.MINE) {
            return data.getActivePerks().stream().filter(p -> PerkRules.isVisible(p, canSeeHidden)).toList();
        }
        return Arrays.stream(Perk.values())
                .filter(p -> p.getCategory() == tab.category)
                .filter(p -> PerkRules.isVisible(p, canSeeHidden))
                .toList();
    }

    // =====================================================================
    // Разметка и кнопки
    // =====================================================================

    @Override
    protected void init() {
        panelHeight = adminMode ? HEIGHT_ADMIN : HEIGHT_SELF;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;

        int bottomBar = adminMode ? 48 : 30;
        bottomY = top + panelHeight - bottomBar;
        listX = left + PAD;
        listY = top + 48;
        listH = bottomY - 6 - listY;
        infoX = listX + LIST_W + 6;
        infoY = listY;
        infoW = left + WIDTH - PAD - infoX;
        infoH = listH;

        // Вкладки
        tabButtons.clear();
        List<Tab> tabs = visibleTabs();
        int gap = 2;
        int tabW = (WIDTH - 2 * PAD - gap * (tabs.size() - 1)) / tabs.size();
        int tx = left + PAD;
        for (Tab t : tabs) {
            Button button = addRenderableWidget(Button.builder(t.title(), b -> selectTab(t))
                    .bounds(tx, top + 26, tabW, 16).build());
            tabButtons.put(t, button);
            tx += tabW + gap;
        }

        // Кнопки в карточке перка. У админа их две: выдать/снять и запретить/разрешить брать
        int buttonY = infoY + infoH - 22;
        if (adminMode) {
            int half = (infoW - 10) / 2;
            actionButton = addRenderableWidget(Button.builder(Component.empty(), b -> onAction())
                    .bounds(infoX + 4, buttonY, half, 18).build());
            blockButton = addRenderableWidget(Button.builder(Component.empty(), b -> onToggleBlock())
                    .bounds(infoX + 6 + half, buttonY, half, 18).build());
        } else {
            actionButton = addRenderableWidget(Button.builder(Component.empty(), b -> onAction())
                    .bounds(infoX + 4, buttonY, infoW - 8, 18).build());
        }

        int by = bottomY + 6;
        if (!adminMode) {
            resetButton = addRenderableWidget(Button.builder(Component.empty(), b -> onReset())
                    .bounds(left + PAD, by, 150, 18).build());
        } else {
            // Ряд 1: баллы и очистка карточки
            small("-10", left + 58, by, 26, () -> admin(Action.ADMIN_POINTS, null, -10));
            small("-1", left + 86, by, 22, () -> admin(Action.ADMIN_POINTS, null, -1));
            small("+1", left + 152, by, 22, () -> admin(Action.ADMIN_POINTS, null, 1));
            small("+10", left + 176, by, 26, () -> admin(Action.ADMIN_POINTS, null, 10));
            wipeButton = addRenderableWidget(Button.builder(Component.empty(), b -> onWipe())
                    .bounds(left + WIDTH - PAD - 120, by, 120, 18).build());

            // Ряд 2: жетоны
            int by2 = by + 22;
            small("-", left + 58, by2, 20, () -> admin(Action.ADMIN_REFUSE_TOKENS, null, -1));
            small("+", left + 108, by2, 20, () -> admin(Action.ADMIN_REFUSE_TOKENS, null, 1));
            small("-", left + 176, by2, 20, () -> admin(Action.ADMIN_RESET_TOKENS, null, -1));
            small("+", left + 226, by2, 20, () -> admin(Action.ADMIN_RESET_TOKENS, null, 1));
            selectionButton = addRenderableWidget(Button.builder(Component.empty(),
                            b -> admin(Action.ADMIN_TOGGLE_SELECTION, null, 0))
                    .bounds(left + WIDTH - PAD - 120, by2, 120, 18).build());
        }
        updateButtons();
    }

    private void small(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 18).build());
    }

    private void selectTab(Tab t) {
        tab = t;
        scroll = 0;
        pendingKey = null;
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingKey != null && Util.getMillis() > pendingUntil) pendingKey = null;
        updateButtons();
    }

    private void updateButtons() {
        tabButtons.forEach((t, b) -> b.active = t != tab);
        PlayerPerks data = data();

        if (blockButton != null) {
            blockButton.visible = data != null && selected != null && selected.getCategory() != Perk.Category.SERVICE;
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

        if (data == null || selected == null || tab == Tab.SKILLS) {
            actionButton.visible = false;
            if (blockButton != null && tab == Tab.SKILLS) blockButton.visible = false;
        } else {
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
        PlayerPerks data = data();
        if (data == null || selected == null) return;
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
        return mx >= listX && mx < listX + LIST_W && my >= listY && my < listY + listH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && inList(mx, my) && tab == Tab.SKILLS) {
            int index = (int) Math.floor((my - listY - 2 + scroll) / SKILL_ROW);
            if (index >= 0 && index < faygolover.rpperks.skill.SkillDef.VALUES.length) {
                selectedSkill = faygolover.rpperks.skill.SkillDef.VALUES[index];
                return true;
            }
            return false;
        }
        if (button == 0 && inList(mx, my)) {
            PlayerPerks data = data();
            if (data == null) return false;
            List<Perk> rows = rows(data);
            int index = (int) Math.floor((my - listY - 2 + scroll) / ROW);
            if (index >= 0 && index < rows.size()) {
                selected = rows.get(index);
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
        g.fill(left - 1, top - 1, left + WIDTH + 1, top + panelHeight + 1, C_BORDER);
        g.fill(left, top, left + WIDTH, top + panelHeight, C_BG);
        g.fill(listX, listY, listX + LIST_W, listY + listH, C_PANEL);
        g.fill(infoX, infoY, infoX + infoW, infoY + infoH, C_PANEL);
        g.fill(left + PAD, bottomY, left + WIDTH - PAD, bottomY + 1, C_BORDER);

        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, title, left + PAD, top + 9, C_TITLE, false);

        PlayerPerks data = data();
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.rpperks.no_data"), left + WIDTH / 2, listY + 20, C_DIM);
            return;
        }

        renderStats(g, data);
        renderList(g, data, mouseX, mouseY);
        renderInfo(g, data);
        renderBottom(g, data);
    }

    private void renderStats(GuiGraphics g, PlayerPerks data) {
        String pointsColor = data.getPoints() < 0 ? "§c" : "§e";
        Component stats = Component.translatable("gui.rpperks.stats",
                pointsColor + data.getPoints(), data.getRefuseTokens(), data.getResetTokens());
        g.drawString(font, stats, left + WIDTH - PAD - font.width(stats), top + 9, C_TEXT, false);
    }

    private void renderList(GuiGraphics g, PlayerPerks data, int mouseX, int mouseY) {
        if (tab == Tab.SKILLS) {
            renderSkills(g, data, mouseX, mouseY);
            return;
        }
        List<Perk> rows = rows(data);
        int contentH = rows.size() * ROW + 4;
        int maxScroll = Math.max(0, contentH - listH);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.rpperks.empty"), listX + LIST_W / 2, listY + 12, C_DIM);
            return;
        }

        g.enableScissor(listX, listY, listX + LIST_W, listY + listH);
        int y = listY + 2 - (int) scroll;
        for (Perk perk : rows) {
            if (y + ROW > listY && y < listY + listH) {
                boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW;
                if (perk == selected) g.fill(listX + 1, y, listX + LIST_W - 1, y + ROW, C_SELECTED);
                else if (hover) g.fill(listX + 1, y, listX + LIST_W - 1, y + ROW, C_HOVER);

                boolean owned = data.owns(perk);
                boolean available = owned || adminMode || PerkRules.whyCannotTake(data, perk, canSeeHidden) == null;

                String cost = costLabel(perk);
                int costX = listX + LIST_W - 8 - font.width(cost);

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
            g.fill(listX + LIST_W - 3, barY, listX + LIST_W - 1, barY + barH, C_DIM);
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
        g.enableScissor(listX, listY, listX + LIST_W, listY + listH);
        int y = listY + 2 - (int) scroll;
        for (var s : skills) {
            float[] v = data.getClientSkills().get(s.key());
            int lvl = v != null ? (int) v[0] : 0;
            float frac = v != null ? v[1] : 0f;
            boolean hover = inList(mouseX, mouseY) && mouseY >= y && mouseY < y + SKILL_ROW;
            if (s == selectedSkill) g.fill(listX + 1, y, listX + LIST_W - 1, y + SKILL_ROW, C_SELECTED);
            else if (hover) g.fill(listX + 1, y, listX + LIST_W - 1, y + SKILL_ROW, C_HOVER);
            g.drawString(font, Component.translatable(s.nameKey()), listX + 4, y + 3, C_TEXT, false);
            String l = String.valueOf(lvl);
            g.drawString(font, l, listX + LIST_W - 8 - font.width(l), y + 3, lvl >= 7 ? C_GOOD : lvl <= 1 ? C_BAD : C_TITLE, false);
            int bx = listX + 4, bw = LIST_W - 12, by = y + 15;
            g.fill(bx, by, bx + bw, by + 4, 0xFF2A2E36);
            for (int i = 1; i < 10; i++) g.fill(bx + bw * i / 10, by, bx + bw * i / 10 + 1, by + 4, 0xFF1B1E24);
            int filled = (int) (bw * Math.min(10, lvl + (lvl >= 10 ? 0 : frac)) / 10f);
            g.fill(bx, by, bx + filled, by + 4, 0xFFD4A537);
            y += SKILL_ROW;
        }
        g.disableScissor();
    }

    private void renderSkillInfo(GuiGraphics g, PlayerPerks data, int x, int y, int w) {
        if (selectedSkill == null) {
            for (FormattedCharSequence line : font.split(Component.translatable("gui.rpperks.skills.hint"), w)) {
                g.drawString(font, line, x, y, C_DIM, false);
                y += 10;
            }
            if (!faygolover.rpperks.Config.get(faygolover.rpperks.Config.SKILL_GROWTH)) {
                y += 4;
                for (FormattedCharSequence line : font.split(Component.translatable("gui.rpperks.skills.growth_off"), w)) {
                    g.drawString(font, line, x, y, C_WARN, false);
                    y += 10;
                }
            }
            return;
        }
        var s = selectedSkill;
        float[] v = data.getClientSkills().get(s.key());
        int lvl = v != null ? (int) v[0] : 0;
        g.drawString(font, Component.translatable(s.nameKey()), x, y, C_TITLE, false);
        y += 12;
        String src = v == null ? "trained" : switch ((int) v[2]) { case 1 -> "perk"; case 2 -> "gm"; default -> "trained"; };
        g.drawString(font, Component.translatable("gui.rpperks.skills.level", lvl).append(" · ")
                .append(Component.translatable("gui.rpperks.skills.source." + src)), x, y, C_TEXT, false);
        y += 12;
        if (v != null && lvl < 10) {
            g.drawString(font, (int) (v[1] * 100) + "%", x, y, C_DIM, false);
            y += 12;
        } else if (lvl >= 10) {
            g.drawString(font, Component.translatable("gui.rpperks.skills.max"), x, y, C_GOOD, false);
            y += 12;
        }
        y += 4;
        for (FormattedCharSequence line : font.split(Component.translatable(s.descKey()), w)) {
            g.drawString(font, line, x, y, C_DIM, false);
            y += 10;
        }
    }

    private void renderInfo(GuiGraphics g, PlayerPerks data) {
        int x = infoX + 6;
        int w = infoW - 12;
        int y = infoY + 6;
        if (tab == Tab.SKILLS) {
            renderSkillInfo(g, data, x, y, w);
            return;
        }

        if (selected == null) {
            for (FormattedCharSequence line : font.split(Component.translatable(adminMode
                    ? "gui.rpperks.select_hint_admin" : "gui.rpperks.select_hint"), w)) {
                g.drawString(font, line, x, y, C_DIM, false);
                y += 10;
            }
            return;
        }
        Perk perk = selected;
        boolean owned = data.owns(perk);

        // Название и цена
        g.drawString(font, Component.translatable(perk.getNameKey()), x, y, C_TEXT, false);
        y += 12;
        Component tabName = Component.translatable("gui.rpperks.tab." + perk.getCategory().getId());
        Component meta = Component.translatable(perk.isPositive() ? "gui.rpperks.cost.positive" : "gui.rpperks.cost.negative",
                tabName, perk.getCost());
        g.drawString(font, meta, x, y, C_DIM, false);
        y += 14;

        // Описание
        for (FormattedCharSequence line : font.split(Component.translatable(perk.getDescKey()), w)) {
            g.drawString(font, line, x, y, C_TEXT, false);
            y += 10;
        }
        y += 4;

        // Несовместимости
        Set<Perk> conflicts = Config.getConflicts(perk);
        if (!conflicts.isEmpty()) {
            Component list = joinNames(conflicts);
            for (FormattedCharSequence line : font.split(Component.translatable("gui.rpperks.conflicts", list), w)) {
                g.drawString(font, line, x, y, C_WARN, false);
                y += 10;
            }
        }

        // Статус над кнопкой
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
            for (FormattedCharSequence line : font.split(s.text, w)) {
                lines.add(line);
                colors.add(s.color);
            }
        }
        int sy = Math.max(y + 2, infoY + infoH - 26 - lines.size() * 10);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, sy + i * 10, colors.get(i), false);
        }
    }

    private record Status(Component text, int color) {}

    private void renderBottom(GuiGraphics g, PlayerPerks data) {
        int by = bottomY + 6;
        if (!adminMode) {
            int hx = left + PAD + 150 + 8;
            int hy = by;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.rpperks.hint"), left + WIDTH - PAD - hx)) {
                g.drawString(font, line, hx, hy, C_DIM, false);
                hy += 10;
            }
            return;
        }
        int textY = by + 5;
        g.drawString(font, Component.translatable("gui.rpperks.label.points"), left + PAD, textY, C_TEXT, false);
        drawCentered(g, (data.getPoints() < 0 ? "§c" : "§e") + data.getPoints(), left + 130, textY);

        int by2 = by + 22 + 5;
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
