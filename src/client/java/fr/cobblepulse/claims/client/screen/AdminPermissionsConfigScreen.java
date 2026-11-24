package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AdminPermissionsConfigScreen : matrice globale des flags (defaut Visiteur / Membre, Modifiable / Verrouille),
 * panneau economique (gain passif, credits staff) et editeur des WorldBorders par dimension.
 */
public class AdminPermissionsConfigScreen extends BaseScreen {
    private JsonObject data;
    private String category = "ALL";
    private String selDim;

    public AdminPermissionsConfigScreen(JsonObject data) {
        super("Configuration administrateur");
        this.data = data;
        this.maxW = 500;
        this.maxH = 310;
        adminTabs(tabs, data);
        tab = data.has("tab") ? data.get("tab").getAsString() : "flags";
        selDim = data.get("dim").getAsString();
    }

    static void adminTabs(List<String[]> tabs, JsonObject data) {
        tabs.clear();
        tabs.add(new String[]{"claims", "Territoires"});
        tabs.add(new String[]{"flags", "Permissions globales"});
        tabs.add(new String[]{"economy", "Économie"});
        tabs.add(new String[]{"borders", "WorldBorders"});
    }

    public void update(JsonObject o) {
        saveDrafts();
        data = o;
        if (o.has("tab") && !o.get("tab").getAsString().equals("claims")) tab = o.get("tab").getAsString();
        rebuildWidgets();
    }

    public JsonObject data() {
        return data;
    }

    public String currentTab() {
        return tab;
    }

    private boolean perm(String k) {
        return data.getAsJsonObject("perms").get(k).getAsBoolean();
    }

    @Override
    protected void switchTab(String t) {
        if (t.equals("claims")) {
            JsonObject copy = data.deepCopy();
            copy.addProperty("tab", "claims");
            minecraft.setScreen(new AdminClaimsHubScreen(copy));
            return;
        }
        super.switchTab(t);
    }

    @Override
    protected String footer() {
        return switch (tab) {
            case "flags" -> "Défauts appliqués à la création · 🔒 = non modifiable par les joueurs";
            case "economy" -> "Les crédits staff dépassent le plafond sans le réinitialiser";
            default -> "Bordures indépendantes de la bordure vanilla";
        };
    }

    private JsonObject border() {
        JsonObject first = null;
        for (JsonElement e : data.getAsJsonArray("borders")) {
            JsonObject b = e.getAsJsonObject();
            if (first == null) first = b;
            if (b.get("dim").getAsString().equals(selDim)) return b;
        }
        if (first != null) selDim = first.get("dim").getAsString();
        return first;
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    @Override
    protected void initWidgets() {
        int x = px + 6, y = py + 40, w = pw - 12;
        switch (tab) {
            case "flags" -> box("search", x, y, 150, "", "Rechercher une permission…", 40);
            case "economy" -> {
                JsonObject e = data.getAsJsonObject("economy");
                String[] keys = {"interval_minutes", "blocks_per_interval", "max_accrued_blocks", "initial_blocks", "afk_minutes"};
                for (int i = 0; i < keys.length; i++) box("eco_" + keys[i], x + 170, y + 14 + i * 18, 60, e.get(keys[i]).getAsString(), "0", 7);
                box("give_name", x + w / 2 + 14, y + 30, 120, "", "Pseudo", 32);
                box("give_amount", x + w / 2 + 140, y + 30, 60, "", "Blocs", 8);
            }
            case "borders" -> {
                JsonObject b = border();
                if (b == null) return;
                int ex = x + 150;
                box("b_cx_" + selDim, ex + 70, y + 46, 60, num(b.get("cx").getAsDouble()), "X", 9);
                box("b_cz_" + selDim, ex + 136, y + 46, 60, num(b.get("cz").getAsDouble()), "Z", 9);
                box("b_r_" + selDim, ex + 70, y + 66, 60, num(b.get("radius").getAsDouble()), "Rayon", 9);
                box("b_sx_" + selDim, ex + 70, y + 86, 60, num(b.get("sx").getAsDouble()), "Largeur X", 9);
                box("b_sz_" + selDim, ex + 136, y + 86, 60, num(b.get("sz").getAsDouble()), "Longueur Z", 9);
                box("edge", ex + 70, y + 130, 40, data.get("edgeBuffer").getAsString(), "2", 3);
            }
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        switch (tab) {
            case "economy" -> renderEconomy(g, x, y, w, h);
            case "borders" -> renderBorders(g, mx, my, x, y, w, h);
            default -> renderFlags(g, mx, my, x, y, w, h);
        }
    }

    // ------------------------------------------------------------------ flags globaux

    private void renderFlags(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        boolean can = perm("config");
        String[][] cats = {{"ALL", "Tous"}, {"A", "A"}, {"B", "B"}, {"C", "C"}, {"D", "D"}, {"E", "E"}, {"F", "F"}};
        int cx = x + 156;
        for (String[] c : cats) {
            cx += button(g, cx, y, c[0].equals("ALL") ? 30 : 18, 14, c[1], c[0].equals(category) ? UiKit.ACCENT : UiKit.MUTED, true, () -> {
                category = c[0];
                scroll.put("gflags", 0);
            }, null) + 2;
        }
        int colV = x + w - 230, colM = x + w - 160, colL = x + w - 88;
        int hy = y + 18;
        g.drawString(font, "Permission", x + 4, hy, UiKit.DIM, false);
        g.drawString(font, "Visiteur", colV + 10, hy, UiKit.DIM, false);
        g.drawString(font, "Membre", colM + 12, hy, UiKit.DIM, false);
        g.drawString(font, "Joueurs", colL + 14, hy, UiKit.DIM, false);
        String q = UiKit.norm(boxValue("search"));
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement e : data.getAsJsonArray("flags")) {
            JsonObject f = e.getAsJsonObject();
            if (!category.equals("ALL") && !category.equals(f.get("cat").getAsString())) continue;
            if (!q.isEmpty() && !UiKit.norm(f.get("label").getAsString() + " " + f.get("id").getAsString() + " " + f.get("desc").getAsString()).contains(q)) continue;
            rows.add(f);
        }
        int listY = hy + 11, listH = h - (listY - y), rowH = 16;
        int s = scrollArea("gflags", x, listY, w, listH, rows.size() * rowH);
        g.enableScissor(x, listY, x + w, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            JsonObject f = rows.get(i);
            int ry = listY + i * rowH - s;
            if (ry + rowH < listY || ry > listY + listH) continue;
            String id = f.get("id").getAsString();
            boolean locked = f.get("locked").getAsBoolean();
            g.fill(x, ry, x + w - 4, ry + rowH - 1, UiKit.isHover(mx, my, x, ry, w - 4, rowH - 1) ? UiKit.CARD_HOVER : UiKit.CARD);
            g.drawString(font, f.get("cat").getAsString(), x + 3, ry + 4, UiKit.DIM, false);
            g.drawString(font, trim((locked ? "🔒 " : "") + f.get("label").getAsString(), colV - x - 20), x + 12, ry + 4, UiKit.TEXT, false);
            tooltip(x, ry, colV - x - 4, rowH, List.of(Component.literal(id).withStyle(st -> st.withColor(0x5C6878)), Component.literal(f.get("desc").getAsString())));
            boolean zone = f.get("zone").getAsBoolean();
            globalSel(g, colV, ry + 1, zone ? 138 : 66, id, "visitor", f.get("gv").getAsString(), can);
            if (!zone) globalSel(g, colM, ry + 1, 66, id, "member", f.get("gm").getAsString(), can);
            button(g, colL, ry + 1, 82, 13, locked ? "🔒 Verrouillée" : "Modifiable", locked ? UiKit.WARN : UiKit.OK, can,
                    () -> send("admin_lock", args("flag", id, "editable", locked)), locked ? "Clic : rendre modifiable par les joueurs" : "Clic : verrouiller (valeur serveur imposée)");
        }
        g.disableScissor();
        scrollbar(g, "gflags", x + w - 3, listY, listH, rows.size() * rowH);
    }

    private void globalSel(GuiGraphics g, int x, int y, int w, String id, String column, String state, boolean can) {
        String next = state.equals("ALLOW") ? "DENY" : "ALLOW";
        button(g, x, y, w, 13, UiKit.stateLabel(state), UiKit.stateColor(state), can,
                () -> send("admin_flag", args("flag", id, "column", column, "state", next)), can ? "Clic : " + UiKit.stateLabel(next) : "cobblepulse.admin.config requis");
    }

    // ------------------------------------------------------------------ economie

    private void renderEconomy(GuiGraphics g, int x, int y, int w, int h) {
        boolean can = perm("config");
        UiKit.panel(g, x, y, w / 2 - 4, h, UiKit.CARD, UiKit.BORDER);
        g.drawString(font, "Gain passif de blocs", x + 6, y + 4, UiKit.GOLD, false);
        String[][] rows = {{"Intervalle (minutes)", "Temps de jeu effectif entre deux paliers"}, {"Blocs par palier", "Surcharge : …limit.blocks_per_interval.<n>"},
                {"Plafond du cumul", "Surcharge : …limit.max_blocks.<n>"}, {"Solde de départ", "Première connexion"}, {"Inactivité (min)", "Au-delà, le temps ne compte plus (0 = jamais)"}};
        for (int i = 0; i < rows.length; i++) {
            g.drawString(font, rows[i][0], x + 6, y + 18 + i * 18, UiKit.TEXT, false);
            tooltip(x + 6, y + 15 + i * 18, 150, 14, List.of(Component.literal(rows[i][1])));
        }
        button(g, x + 6, y + 108, 100, 14, "Enregistrer", UiKit.OK, can, () -> {
            JsonObject o = new JsonObject();
            for (String k : new String[]{"interval_minutes", "blocks_per_interval", "max_accrued_blocks", "initial_blocks", "afk_minutes"}) {
                try {
                    o.addProperty(k, Integer.parseInt(boxValue("eco_" + k).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            send("admin_economy", o);
        }, can ? null : "cobblepulse.admin.config requis");
        JsonObject e = data.getAsJsonObject("economy");
        g.drawString(font, trim("Actuel : +" + e.get("blocks_per_interval").getAsInt() + " / " + e.get("interval_minutes").getAsInt() + " min, plafond "
                + e.get("max_accrued_blocks").getAsInt(), w / 2 - 16), x + 6, y + 130, UiKit.MUTED, false);

        int rx = x + w / 2 + 4, rw = w / 2 - 4;
        boolean give = perm("give");
        UiKit.panel(g, rx, y, rw, h, UiKit.CARD, UiKit.BORDER);
        g.drawString(font, "Créditer / débiter un joueur", rx + 6, y + 4, UiKit.GOLD, false);
        g.drawString(font, "Pseudo et nombre de blocs :", rx + 6, y + 18, UiKit.MUTED, false);
        int bx = rx + 6;
        for (String[] op : new String[][]{{"add", "Ajouter"}, {"remove", "Retirer"}, {"set", "Définir le total"}}) {
            bx += autoButton(g, bx, y + 52, op[1], op[0].equals("remove") ? UiKit.ERROR : UiKit.OK, give, () -> {
                int amount;
                try {
                    amount = Integer.parseInt(boxValue("give_amount").trim());
                } catch (NumberFormatException ex) {
                    showResult(false, "Nombre de blocs invalide.");
                    return;
                }
                send("admin_blocks", args("name", boxValue("give_name").trim(), "op", op[0], "amount", amount));
            }, give ? null : "cobblepulse.admin.give_blocks requis") + 3;
        }
        g.drawString(font, "Commande : /claimadmin blocs <joueur>", rx + 6, y + 76, UiKit.DIM, false);
        g.drawString(font, "ajouter | retirer | definir <n>", rx + 6, y + 87, UiKit.DIM, false);
    }

    // ------------------------------------------------------------------ WorldBorders

    private void renderBorders(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        boolean can = perm("worldborder");
        int lw = 144;
        UiKit.panel(g, x, y, lw, h, UiKit.CARD, UiKit.BORDER);
        int ly = y + 4;
        for (JsonElement e : data.getAsJsonArray("borders")) {
            JsonObject b = e.getAsJsonObject();
            String d = b.get("dim").getAsString();
            boolean on = d.equals(selDim);
            button(g, x + 3, ly, lw - 6, 14, (b.get("enabled").getAsBoolean() ? "● " : "○ ") + UiKit.dimName(d), on ? UiKit.ACCENT : b.get("enabled").getAsBoolean() ? UiKit.TEXT : UiKit.DIM,
                    true, () -> {
                        saveDrafts();
                        selDim = d;
                        rebuildWidgets();
                    }, d);
            ly += 16;
        }
        JsonObject b = border();
        if (b == null) return;
        int ex = x + lw + 6;
        int ew = w - lw - 6;
        UiKit.panel(g, ex, y, ew, h, UiKit.CARD, UiKit.BORDER);
        g.drawString(font, trim(selDim, ew - 12), ex + 6, y + 4, UiKit.GOLD, false);
        boolean enabled = b.get("enabled").getAsBoolean();
        boolean circle = "CIRCLE".equals(b.get("shape").getAsString());
        autoButton(g, ex + 6, y + 20, enabled ? "Activée" : "Désactivée", enabled ? UiKit.OK : UiKit.MUTED, can,
                () -> send("admin_border", args("dim", selDim, "enabled", !enabled)), null);
        autoButton(g, ex + 80, y + 20, circle ? "Forme : Cercle" : "Forme : Rectangle", UiKit.ACCENT, can,
                () -> send("admin_border", args("dim", selDim, "shape", circle ? "RECTANGLE" : "CIRCLE")), null);
        g.drawString(font, "Centre", ex + 6, y + 50, UiKit.TEXT, false);
        autoButton(g, ex + 204, y + 46, "Ma position", UiKit.MUTED, can && ClientState.dim.equals(selDim), () -> {
            if (minecraft.player == null) return;
            var bx = boxes.get("b_cx_" + selDim);
            var bz = boxes.get("b_cz_" + selDim);
            if (bx != null) bx.setValue(String.valueOf(minecraft.player.getBlockX()));
            if (bz != null) bz.setValue(String.valueOf(minecraft.player.getBlockZ()));
        }, "Centre = votre position actuelle");
        g.drawString(font, "Rayon", ex + 6, y + 70, circle ? UiKit.TEXT : UiKit.DIM, false);
        g.drawString(font, "Taille X / Z", ex + 6, y + 90, circle ? UiKit.DIM : UiKit.TEXT, false);
        button(g, ex + 6, y + 108, 100, 14, "Enregistrer", UiKit.OK, can, () -> {
            try {
                send("admin_border", args("dim", selDim, "cx", Double.parseDouble(boxValue("b_cx_" + selDim).trim()), "cz",
                        Double.parseDouble(boxValue("b_cz_" + selDim).trim()), "radius", Double.parseDouble(boxValue("b_r_" + selDim).trim()), "sx",
                        Double.parseDouble(boxValue("b_sx_" + selDim).trim()), "sz", Double.parseDouble(boxValue("b_sz_" + selDim).trim())));
            } catch (NumberFormatException ex2) {
                showResult(false, "Valeur numérique invalide.");
            }
        }, can ? null : "cobblepulse.admin.worldborder requis");
        g.drawString(font, "Marge anti-grief", ex + 6, y + 134, UiKit.TEXT, false);
        button(g, ex + 116, y + 130, 60, 14, "Appliquer", UiKit.OK, can, () -> {
            try {
                send("admin_edge", args("value", Integer.parseInt(boxValue("edge").trim())));
            } catch (NumberFormatException ignored) {
            }
        }, "Blocs à l'intérieur de la bordure où le grief reste bloqué");
        // apercu
        int vx = ex + ew - 96, vy = y + 108, vs = Math.min(88, h - 116);
        if (vs > 30) {
            g.fill(vx, vy, vx + vs, vy + vs, 0xFF0E1218);
            double span = circle ? b.get("radius").getAsDouble() * 2 : Math.max(b.get("sx").getAsDouble(), b.get("sz").getAsDouble());
            double sc = (vs - 10) / Math.max(1, span);
            int ccx = vx + vs / 2, ccz = vy + vs / 2;
            int col = enabled ? 0xFFFF5A5A : 0xFF555555;
            if (circle) {
                int r = (int) (b.get("radius").getAsDouble() * sc);
                for (int a = 0; a < 360; a += 3) {
                    int px2 = ccx + (int) (Math.cos(Math.toRadians(a)) * r), pz2 = ccz + (int) (Math.sin(Math.toRadians(a)) * r);
                    g.fill(px2, pz2, px2 + 1, pz2 + 1, col);
                }
            } else {
                int hx = (int) (b.get("sx").getAsDouble() / 2 * sc), hz = (int) (b.get("sz").getAsDouble() / 2 * sc);
                UiKit.panel(g, ccx - hx, ccz - hz, hx * 2, hz * 2, 0x00000000, col);
            }
            if (ClientState.dim.equals(selDim) && minecraft.player != null) {
                int ppx = ccx + (int) ((minecraft.player.getX() - b.get("cx").getAsDouble()) * sc), ppz = ccz + (int) ((minecraft.player.getZ() - b.get("cz").getAsDouble()) * sc);
                if (UiKit.in(ppx, ppz, vx, vy, vs, vs)) g.fill(ppx - 1, ppz - 1, ppx + 2, ppz + 2, UiKit.GOLD);
            }
            g.drawString(font, "Aperçu", vx + 2, vy + 2, UiKit.DIM, false);
        }
    }
}
