package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonObject;
import fr.cobblepulse.claims.client.ClaimsClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base des ecrans natifs : panneau, onglets, boutons « cartes » dessines a la main (zones cliquables), listes
 * defilantes, champs de saisie conserves entre deux rafraichissements, ligne de resultat du serveur.
 */
public abstract class BaseScreen extends Screen {
    protected int px, py, pw, ph;
    protected String tab;
    protected final List<String[]> tabs = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, ScrollArea> areas = new HashMap<>();
    protected final Map<String, Integer> scroll = new HashMap<>();
    protected final Map<String, String> drafts = new HashMap<>();
    protected final Map<String, EditBox> boxes = new HashMap<>();
    private String resultText;
    private boolean resultOk;
    private long resultAt;
    private List<Component> pendingTooltip;
    protected int maxW = 470, maxH = 300;
    protected int mouseX, mouseY;
    /** Index des zones du panneau modal : les infobulles du dessous sont ignorees. */
    private int overlayFrom;

    private record Hit(int x, int y, int w, int h, Runnable click, Runnable right, List<Component> tooltip) {}

    private record ScrollArea(int x, int y, int w, int h, int content) {}

    protected BaseScreen(String title) {
        super(Component.literal(title));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, maxW);
        ph = Math.min(height - 16, maxH);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        boxes.clear();
        initWidgets();
    }

    /** Ajoute les champs de saisie de l'onglet courant. */
    protected void initWidgets() {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // fond translucide sans flou (le jeu reste visible derriere)
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouseX = mx;
        mouseY = my;
        hits.clear();
        areas.clear();
        pendingTooltip = null;
        g.fill(0, 0, width, height, 0x88000000);
        UiKit.panel(g, px, py, pw, ph, UiKit.PANEL, UiKit.BORDER);
        g.fill(px + 1, py + 1, px + pw - 1, py + 20, UiKit.HEADER);
        g.drawString(font, title, px + 8, py + 6, UiKit.GOLD, false);
        String sub = subtitle();
        if (sub != null) {
            int tw = font.width(title);
            g.drawString(font, trim(sub, pw - tw - 40), px + 14 + tw, py + 6, UiKit.MUTED, false);
        }
        button(g, px + pw - 16, py + 4, 12, 12, "✕", UiKit.MUTED, true, this::onClose, null);
        int tabY = py + 22;
        if (!tabs.isEmpty()) {
            int x = px + 6;
            for (String[] t : tabs) {
                int w = font.width(t[1]) + 14;
                boolean on = t[0].equals(tab);
                int fx = x;
                hit(fx, tabY, w, 14, () -> switchTab(t[0]), null, null);
                g.fill(fx, tabY, fx + w, tabY + 14, on ? UiKit.CARD_ON : UiKit.isHover(mx, my, fx, tabY, w, 14) ? UiKit.CARD_HOVER : UiKit.CARD);
                if (on) g.fill(fx, tabY + 13, fx + w, tabY + 14, UiKit.ACCENT);
                g.drawString(font, t[1], fx + 7, tabY + 3, on ? UiKit.TEXT : UiKit.MUTED, false);
                x += w + 3;
            }
        }
        int top = tabs.isEmpty() ? py + 24 : py + 40;
        renderContent(g, mx, my, pt, px + 6, top, pw - 12, py + ph - 18 - top);
        // ligne de resultat
        if (resultText != null && System.currentTimeMillis() - resultAt < 6000) {
            g.drawString(font, trim((resultOk ? "✔ " : "✖ ") + resultText, pw - 16), px + 8, py + ph - 13, resultOk ? UiKit.OK : UiKit.ERROR, false);
        } else {
            String f = footer();
            if (f != null) g.drawString(font, trim(f, pw - 16), px + 8, py + ph - 13, UiKit.DIM, false);
        }
        overlayFrom = 0;
        renderOverlay(g, mx, my, pt);
        super.render(g, mx, my, pt);
        if (pendingTooltip == null) {
            for (int i = hits.size() - 1; i >= overlayFrom; i--) {
                Hit h = hits.get(i);
                if (h.tooltip != null && UiKit.in(mx, my, h.x, h.y, h.w, h.h)) {
                    pendingTooltip = h.tooltip;
                    break;
                }
            }
        }
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mx, my);
    }

    protected abstract void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h);

    /** Dessine par-dessus (sous-panneaux modaux) ; les champs de saisie restent au premier plan. */
    protected void renderOverlay(GuiGraphics g, int mx, int my, float pt) {}

    /** Debut d'un panneau modal : bloque les clics et les infobulles de ce qui est dessous. */
    protected void beginModal() {
        hits.add(new Hit(0, 0, width, height, () -> {
        }, () -> {
        }, null));
        overlayFrom = hits.size();
    }

    protected String subtitle() {
        return null;
    }

    protected String footer() {
        return null;
    }

    protected void switchTab(String t) {
        if (t.equals(tab)) return;
        saveDrafts();
        tab = t;
        rebuildWidgets();
    }

    /** Tests : changer d'onglet comme un clic. */
    public void selectTab(String t) {
        switchTab(t);
    }

    public String tab() {
        return tab;
    }

    public String lastResult() {
        return resultText;
    }

    public void showResult(boolean ok, String msg) {
        resultOk = ok;
        resultText = msg;
        resultAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ zones cliquables

    protected void hit(int x, int y, int w, int h, Runnable click, Runnable right, List<Component> tooltip) {
        hits.add(new Hit(x, y, w, h, click, right, tooltip));
    }

    protected void tooltip(int x, int y, int w, int h, List<Component> tooltip) {
        hits.add(new Hit(x, y, w, h, null, null, tooltip));
    }

    /** Bouton plat ; renvoie sa largeur. */
    protected int button(GuiGraphics g, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable click, String tip) {
        boolean hover = enabled && UiKit.in(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? UiKit.CARD_ON : UiKit.CARD);
        g.fill(x, y + h - 1, x + w, y + h, enabled ? UiKit.withAlpha(color, 0x90) : UiKit.BORDER);
        String t = trim(label, w - 4);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2, enabled ? color : UiKit.DIM, false);
        hit(x, y, w, h, enabled ? click : null, null, tip == null ? null : List.of(Component.literal(tip)));
        return w;
    }

    protected int autoButton(GuiGraphics g, int x, int y, String label, int color, boolean enabled, Runnable click, String tip) {
        return button(g, x, y, font.width(label) + 12, 14, label, color, enabled, click, tip);
    }

    protected String trim(String s, int w) {
        if (s == null) return "";
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    // ------------------------------------------------------------------ listes defilantes

    /** Declare une zone defilante et renvoie le decalage courant (borne). */
    protected int scrollArea(String key, int x, int y, int w, int h, int content) {
        areas.put(key, new ScrollArea(x, y, w, h, content));
        int max = Math.max(0, content - h);
        int s = Math.max(0, Math.min(max, scroll.getOrDefault(key, 0)));
        scroll.put(key, s);
        return s;
    }

    protected void scrollbar(GuiGraphics g, String key, int x, int y, int h, int content) {
        if (content <= h) return;
        int s = scroll.getOrDefault(key, 0);
        int barH = Math.max(12, h * h / content);
        int barY = y + (int) ((long) (h - barH) * s / Math.max(1, content - h));
        g.fill(x, y, x + 3, y + h, 0x40FFFFFF);
        g.fill(x, barY, x + 3, barY + barH, UiKit.ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        for (Map.Entry<String, ScrollArea> e : areas.entrySet()) {
            ScrollArea a = e.getValue();
            if (UiKit.in(mx, my, a.x, a.y, a.w, a.h)) {
                int max = Math.max(0, a.content - a.h);
                scroll.put(e.getKey(), Math.max(0, Math.min(max, scroll.getOrDefault(e.getKey(), 0) - (int) (dy * 16))));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (var w : children()) {
            if (w instanceof EditBox b && !b.isMouseOver(mx, my)) b.setFocused(false);
        }
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!UiKit.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            Runnable r = button == 1 ? h.right : h.click;
            if (r == null) {
                if (h.click != null || h.right != null) return true;
                continue;
            }
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            r.run();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ champs de saisie

    protected EditBox box(String key, int x, int y, int w, String value, String hint, int maxLen) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.literal(hint));
        b.setMaxLength(maxLen);
        b.setValue(drafts.containsKey(key) ? drafts.get(key) : value == null ? "" : value);
        b.setHint(Component.literal(hint).withStyle(s -> s.withColor(0x5C6878)));
        b.setResponder(v -> drafts.put(key, v));
        addRenderableWidget(b);
        boxes.put(key, b);
        return b;
    }

    protected String boxValue(String key) {
        EditBox b = boxes.get(key);
        return b == null ? drafts.getOrDefault(key, "") : b.getValue();
    }

    protected void saveDrafts() {
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) drafts.put(e.getKey(), e.getValue().getValue());
    }

    protected void clearDraft(String key) {
        drafts.remove(key);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            onClose();
            return true;
        }
        for (var w : children()) {
            if (w instanceof EditBox b && b.isFocused()) {
                if (key == 257 || key == 335) {
                    onEnter(b);
                    return true;
                }
                return b.keyPressed(key, scan, mods) || b.canConsumeInput();
            }
        }
        if (minecraft.options.keyInventory.matches(key, scan) || ClaimsClient.CONTEXT_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Entree dans un champ. */
    protected void onEnter(EditBox b) {}

    protected static JsonObject args(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            String k = String.valueOf(kv[i]);
            if (v instanceof Number n) o.addProperty(k, n);
            else if (v instanceof Boolean b) o.addProperty(k, b);
            else if (v instanceof com.google.gson.JsonElement e) o.add(k, e);
            else o.addProperty(k, v == null ? null : String.valueOf(v));
        }
        return o;
    }

    protected static void send(String action, JsonObject args) {
        ClaimsClient.action(action, args);
    }
}
