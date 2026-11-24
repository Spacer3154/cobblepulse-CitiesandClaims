package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** AdminClaimsHubScreen : tous les territoires du serveur, recherche, gestion, teleportation, suppression, outils staff. */
public class AdminClaimsHubScreen extends BaseScreen {
    private JsonObject data;
    private String filter = "ALL";
    private String armed;
    private long armedAt;

    public AdminClaimsHubScreen(JsonObject data) {
        super("Administration des territoires");
        this.data = data;
        this.maxW = 500;
        this.maxH = 310;
        AdminPermissionsConfigScreen.adminTabs(tabs, data);
        tab = "claims";
    }

    public void update(JsonObject o) {
        saveDrafts();
        data = o;
        rebuildWidgets();
    }

    public JsonObject data() {
        return data;
    }

    private boolean perm(String k) {
        return data.getAsJsonObject("perms").get(k).getAsBoolean();
    }

    @Override
    protected void switchTab(String t) {
        if (t.equals(tab)) return;
        JsonObject copy = data.deepCopy();
        copy.addProperty("tab", t);
        minecraft.setScreen(new AdminPermissionsConfigScreen(copy));
    }

    @Override
    protected String subtitle() {
        return data.get("claimCount").getAsInt() + " territoire(s)";
    }

    @Override
    protected String footer() {
        return "Staff · nœuds cobblepulse.staff.* et cobblepulse.admin.*";
    }

    @Override
    protected void initWidgets() {
        box("search", px + 6, py + 40, 140, "", "Nom, joueur, clan, n°…", 40);
    }

    private boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(armed) && now - armedAt < 4000) {
            armed = null;
            return true;
        }
        armed = key;
        armedAt = now;
        return false;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        int fx = x + 146;
        for (String[] f : new String[][]{{"ALL", "Tous"}, {"PLAYER", "Joueurs"}, {"ADMIN", "Admin"}, {"DIM", "Ici"}}) {
            fx += button(g, fx, y, font.width(f[1]) + 10, 14, f[1], f[0].equals(filter) ? UiKit.ACCENT : UiKit.MUTED, true, () -> {
                filter = f[0];
                scroll.put("claims", 0);
            }, f[0].equals("DIM") ? "Dimension actuelle" : null) + 2;
        }
        int rx = x + w;
        if (perm("convert")) {
            boolean on = data.get("adminMode").getAsBoolean();
            String l = on ? "Balise : admin" : "Balise : joueur";
            rx -= font.width(l) + 12;
            autoButton(g, rx, y, l, on ? UiKit.VIOLET : UiKit.MUTED, true, () -> send("admin_mode", args("tab", "claims")),
                    "Mode de création de la Balise (claims admin sans coût)");
            rx -= 3;
        }
        if (perm("bypass")) {
            boolean on = data.get("bypassOn").getAsBoolean();
            String l = on ? "Contournement : ON" : "Contournement : OFF";
            rx -= font.width(l) + 12;
            autoButton(g, rx, y, l, on ? UiKit.WARN : UiKit.MUTED, true, () -> send("admin_bypass", args("tab", "claims")),
                    "cobblepulse.staff.bypass : ignorer toutes les protections");
        }
        String q = UiKit.norm(boxValue("search"));
        String here = data.get("dim").getAsString();
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement e : data.getAsJsonArray("claims")) {
            JsonObject c = e.getAsJsonObject();
            if (filter.equals("PLAYER") && "ADMIN".equals(c.get("type").getAsString())) continue;
            if (filter.equals("ADMIN") && !"ADMIN".equals(c.get("type").getAsString())) continue;
            if (filter.equals("DIM") && !here.equals(c.get("dim").getAsString())) continue;
            if (!q.isEmpty()) {
                String hay = UiKit.norm(c.get("name").getAsString() + " " + c.get("owner").getAsString() + " " + (c.has("clan") ? c.get("clan").getAsString() : "")
                        + " n°" + c.get("id").getAsInt() + " " + c.get("id").getAsInt());
                if (!hay.contains(q)) continue;
            }
            rows.add(c);
        }
        int listY = y + 20, listH = h - 20, rowH = 24;
        int s = scrollArea("claims", x, listY, w, listH, rows.size() * rowH);
        g.enableScissor(x, listY, x + w, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            JsonObject c = rows.get(i);
            int ry = listY + i * rowH - s;
            if (ry + rowH < listY || ry > listY + listH) continue;
            int id = c.get("id").getAsInt();
            int rgb = ClaimManagerScreen.relColor(c.get("rel").getAsString());
            g.fill(x, ry, x + w - 4, ry + rowH - 2, UiKit.isHover(mx, my, x, ry, w - 4, rowH - 2) ? UiKit.CARD_HOVER : UiKit.CARD);
            g.fill(x, ry, x + 2, ry + rowH - 2, UiKit.withAlpha(rgb, 0xFF));
            g.drawString(font, "n°" + id, x + 6, ry + 3, UiKit.DIM, false);
            g.drawString(font, trim(c.get("name").getAsString(), w - 230), x + 36, ry + 3, UiKit.TEXT, false);
            String clan = c.has("clan") ? " [" + c.get("clan").getAsString() + "]" : "";
            int cxm = (c.get("minX").getAsInt() + c.get("maxX").getAsInt()) / 2, czm = (c.get("minZ").getAsInt() + c.get("maxZ").getAsInt()) / 2;
            g.drawString(font, trim(c.get("owner").getAsString() + clan + " · " + UiKit.dimName(c.get("dim").getAsString()) + " (" + cxm + ", " + czm + ") · "
                    + c.get("area").getAsLong() + " blocs · " + c.get("subs").getAsInt() + " parc. · " + c.get("members").getAsInt() + " memb.", w - 200), x + 6, ry + 13, UiKit.DIM, false);
            int bx = x + w - 188;
            bx += button(g, bx, ry + 4, 56, 14, "Gérer", UiKit.ACCENT, true, () -> send("open_manager", args("id", id)), null) + 3;
            bx += button(g, bx, ry + 4, 56, 14, "TP", UiKit.MUTED, perm("inspect"), () -> send("tp", args("id", id)), "Se téléporter au centre") + 3;
            String key = "del" + id;
            boolean armedNow = key.equals(armed) && System.currentTimeMillis() - armedAt < 4000;
            button(g, bx, ry + 4, 64, 14, armedNow ? "Confirmer ?" : "Supprimer", UiKit.ERROR, perm("delete"), () -> {
                if (confirm(key)) send("delete", args("id", id, "from", "admin"));
            }, "cobblepulse.staff.delete_others");
        }
        g.disableScissor();
        scrollbar(g, "claims", x + w - 3, listY, listH, rows.size() * rowH);
        if (rows.isEmpty()) g.drawString(font, "Aucun territoire.", x + 6, listY + 6, UiKit.MUTED, false);
        if (ClientState.staff && rows.size() >= 3000) g.drawString(font, "(3000 plus récents)", x + 6, y + h - 8, UiKit.DIM, false);
    }
}
