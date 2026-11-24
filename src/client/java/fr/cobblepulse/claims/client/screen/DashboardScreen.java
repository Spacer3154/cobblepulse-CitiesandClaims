package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/** Tableau de bord global (dans la nature) : claims, solde de blocs residuels / max, compte a rebours du prochain palier. */
public class DashboardScreen extends BaseScreen {
    private JsonObject data;
    private long receivedAt;

    public DashboardScreen(JsonObject data) {
        super("Mes territoires");
        this.maxW = 460;
        this.maxH = 300;
        update0(data);
        tab = "mine";
        tabs.add(new String[]{"mine", "Mes claims"});
        tabs.add(new String[]{"trusted", "Où je suis membre"});
    }

    private void update0(JsonObject o) {
        data = o;
        receivedAt = System.currentTimeMillis();
    }

    public void update(JsonObject o) {
        update0(o);
    }

    public JsonObject data() {
        return data;
    }

    private long l(String k) {
        return data.has(k) ? data.get(k).getAsLong() : 0;
    }

    /** Millisecondes restantes avant le prochain palier (decompte local), -1 si plafond atteint. */
    public long remaining() {
        long u = l("untilNext");
        if (u < 0) return -1;
        if (data.get("afk").getAsBoolean()) return u;
        return Math.max(0, u - (System.currentTimeMillis() - receivedAt));
    }

    @Override
    protected String footer() {
        return "K dans un territoire : le gérer · Maj + K : administration (staff)";
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        // bandeau du solde
        int bh = 52;
        UiKit.panel(g, x, y, w, bh, UiKit.CARD, UiKit.BORDER);
        int cy = y + 4;
        stat(g, x + 8, cy, "Disponibles", String.valueOf(l("available")), UiKit.GOLD);
        stat(g, x + 98, cy, "Total", String.valueOf(l("total")), UiKit.TEXT);
        stat(g, x + 168, cy, "Utilisés", String.valueOf(l("used")), UiKit.MUTED);
        long max = l("maxClaims");
        stat(g, x + 238, cy, "Territoires", data.getAsJsonArray("claims").size() + " / " + (max < 0 ? "∞" : String.valueOf(max)), UiKit.ACCENT);
        // prochain palier
        long rem = remaining();
        long interval = l("intervalMin") * 60_000L;
        int barX = x + 8, barY = cy + 26, barW = w - 16;
        g.fill(barX, barY, barX + barW, barY + 6, 0xFF0E1218);
        String label;
        if (!data.get("accrue").getAsBoolean()) {
            label = "Attribution passive désactivée pour votre grade.";
        } else if (rem < 0) {
            g.fill(barX, barY, barX + barW, barY + 6, 0xFF6B5A20);
            label = "Plafond du cumul passif atteint (" + l("cap") + " blocs). Les crédits du staff restent possibles.";
        } else {
            int filled = (int) (barW * Math.max(0, Math.min(1, (interval - rem) / (double) Math.max(1, interval))));
            g.fill(barX, barY, barX + filled, barY + 6, UiKit.ACCENT);
            label = "Prochain palier : +" + l("perInterval") + " blocs dans " + UiKit.fmtDuration(rem) + (data.get("afk").getAsBoolean() ? " (en pause : inactif)" : "")
                    + " · cumul " + l("accrued") + " / " + l("cap");
        }
        g.drawString(font, trim(label, barW), barX, barY + 9, UiKit.MUTED, false);

        int listY = y + bh + 4, listH = h - (listY - y) - 18;
        JsonArray arr = data.getAsJsonArray("trusted".equals(tab) ? "trusted" : "claims");
        int rowH = 24;
        int s = scrollArea("list", x, listY, w, listH, arr.size() * rowH);
        g.enableScissor(x, listY, x + w, listY + listH);
        int i = 0;
        for (JsonElement e : arr) {
            JsonObject c = e.getAsJsonObject();
            int ry = listY + i * rowH - s;
            int rgb = ClaimManagerScreen.relColor(c.get("rel").getAsString());
            g.fill(x, ry, x + w - 4, ry + rowH - 2, UiKit.isHover(mx, my, x, ry, w - 4, rowH - 2) ? UiKit.CARD_HOVER : UiKit.CARD);
            g.fill(x, ry, x + 2, ry + rowH - 2, UiKit.withAlpha(rgb, 0xFF));
            g.drawString(font, trim(c.get("name").getAsString(), w - 150), x + 6, ry + 3, UiKit.TEXT, false);
            int cxm = (c.get("minX").getAsInt() + c.get("maxX").getAsInt()) / 2, czm = (c.get("minZ").getAsInt() + c.get("maxZ").getAsInt()) / 2;
            String info = UiKit.dimName(c.get("dim").getAsString()) + " (" + cxm + ", " + czm + ") · " + c.get("area").getAsLong() + " blocs";
            if (c.has("subs")) info += " · " + c.get("subs").getAsInt() + " parcelle(s) · " + c.get("members").getAsInt() + " membre(s)";
            if (c.has("level")) info += " · " + c.get("level").getAsString() + " · " + c.get("owner").getAsString();
            g.drawString(font, trim(info, w - 90), x + 6, ry + 13, UiKit.DIM, false);
            int id = c.get("id").getAsInt();
            button(g, x + w - 76, ry + 4, 68, 14, c.has("level") ? "Fiche" : "Gérer", UiKit.ACCENT, true, () -> send("open_manager", args("id", id)), null);
            i++;
        }
        g.disableScissor();
        scrollbar(g, "list", x + w - 3, listY, listH, arr.size() * rowH);
        if (arr.isEmpty()) {
            g.drawString(font, "trusted".equals(tab) ? "Personne ne vous a encore ajouté (/trust)." : "Aucun territoire : prenez la Balise (/claim outils)",
                    x + 6, listY + 6, UiKit.MUTED, false);
            if (!"trusted".equals(tab)) g.drawString(font, "et cliquez deux coins opposés.", x + 6, listY + 17, UiKit.MUTED, false);
        }
        int by = y + h - 14;
        int bx = x;
        bx += autoButton(g, bx, by, "⚙ Options client", UiKit.MUTED, true, () -> minecraft.setScreen(new ClientOptionsScreen(this)),
                "Cartes (JourneyMap, Xaero), rendu 3D") + 4;
        if (data.get("staff").getAsBoolean())
            autoButton(g, bx, by, "Staff", UiKit.VIOLET, true, () -> send("open_admin", args("tab", "claims")), "AdminClaimsHubScreen");
        button(g, x + w - 70, by, 70, 14, "Fermer", UiKit.MUTED, true, this::onClose, null);
    }

    private void stat(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(font, label, x, y, UiKit.DIM, false);
        g.drawString(font, value, x, y + 11, color, false);
    }
}
