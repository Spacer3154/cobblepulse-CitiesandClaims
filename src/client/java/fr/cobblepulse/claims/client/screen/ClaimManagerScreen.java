package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ClaimManagerScreen : nom de la ville, matrice des permissions (recherche, categories, selecteur 3 etats, cadenas),
 * sous-panneau des commandes, membres et roles, parcelles, apercu 2D et bascule du rendu 3D, conversion staff.
 */
public class ClaimManagerScreen extends BaseScreen {
    private JsonObject data;
    private String category = "ALL";
    private boolean cmdOpen;
    private String cmdMode;
    private final List<String> cmdList = new ArrayList<>();
    private String newLevel = "BUILD";
    private String armed;
    private long armedAt;

    private static final String[][] LEVELS = {{"ACCESS", "Accès", "Boutons, leviers, lits, portes, soigneur et PC Cobblemon"},
            {"CONTAINER", "Conteneurs", "Accès + coffres, tonneaux, shulkers, fours"},
            {"BUILD", "Membre", "Conteneurs + poser/casser, récoltes, captures et combats"},
            {"MANAGER", "Co-Maire", "Membre + gérer les membres, les parcelles et les permissions"}};

    public ClaimManagerScreen(JsonObject data) {
        super("Gestion du territoire");
        this.data = data;
        this.maxW = 480;
        this.maxH = 310;
        tab = data.has("tab") ? data.get("tab").getAsString() : "general";
        buildTabs();
    }

    private void buildTabs() {
        tabs.clear();
        tabs.add(new String[]{"general", "Général"});
        tabs.add(new String[]{"perms", "Permissions"});
        tabs.add(new String[]{"members", "Membres & Rôles"});
        tabs.add(new String[]{"subs", "Parcelles"});
        if (perm("settings")) tabs.add(new String[]{"settings", "Réglages admin"});
    }

    public int claimId() {
        return claim().get("id").getAsInt();
    }

    public String currentTab() {
        return tab;
    }

    public JsonObject data() {
        return data;
    }

    public void update(JsonObject o) {
        boolean sameClaim = o.getAsJsonObject("claim").get("id").getAsInt() == claimId();
        if (sameClaim) saveDrafts();
        else drafts.clear();
        this.data = o;
        if (o.has("tab")) tab = o.get("tab").getAsString();
        if (cmdOpen) loadCommands();
        buildTabs();
        rebuildWidgets();
    }

    private JsonObject claim() {
        return data.getAsJsonObject("claim");
    }

    private boolean perm(String k) {
        JsonObject p = data.getAsJsonObject("perms");
        return p != null && p.has(k) && p.get(k).getAsBoolean();
    }

    private String cs(String k) {
        JsonObject c = claim();
        return c.has(k) && !c.get(k).isJsonNull() ? c.get(k).getAsString() : "";
    }

    private int ci(String k) {
        return claim().has(k) ? claim().get(k).getAsInt() : 0;
    }

    private boolean isSub() {
        return ci("parent") != 0;
    }

    @Override
    protected String subtitle() {
        return cs("name") + " · n°" + ci("id");
    }

    @Override
    protected String footer() {
        return "Rôle : " + switch (data.get("role").getAsString()) {
            case "OWNER" -> "Maire (propriétaire)";
            case "MANAGER" -> "Co-Maire";
            case "STAFF" -> "Staff";
            default -> data.get("role").getAsString();
        } + " · K : fermer";
    }

    @Override
    protected void initWidgets() {
        int x = px + 6, y = py + 40, w = pw - 12;
        switch (tab) {
            case "general" -> box("name", x + 4, y + 16, (int) (w * 0.52) - 76, cs("rawName"), isSub() ? "Nom de la parcelle" : "Nom de la ville", 32);
            case "perms" -> {
                EditBox s = box("search", x, y, 150, "", "Rechercher une permission…", 40);
                s.setVisible(!cmdOpen);
                if (cmdOpen) box("cmdEntry", px + pw / 2 - 120, py + ph / 2 + 50, 150, "", "Préfixe (ex : sethome)", 48);
            }
            case "members" -> box("member", x, y + 14, 140, "", "Pseudo ou « public »", 32);
            case "settings" -> {
                JsonObject st = data.getAsJsonObject("settings");
                box("time", x + 110, y + 40, 60, st.get("time").getAsString(), "6000", 6);
                box("cap", x + 110, y + 82, 60, st.get("level_cap").getAsString(), "50", 4);
            }
            default -> {
            }
        }
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

    private String armedLabel(String key, String label) {
        return key.equals(armed) && System.currentTimeMillis() - armedAt < 4000 ? "Confirmer ?" : label;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        switch (tab) {
            case "perms" -> renderPerms(g, mx, my, x, y, w, h);
            case "members" -> renderMembers(g, mx, my, x, y, w, h);
            case "subs" -> renderSubs(g, mx, my, x, y, w, h);
            case "settings" -> renderSettings(g, x, y, w, h);
            default -> renderGeneral(g, mx, my, x, y, w, h);
        }
    }

    // ------------------------------------------------------------------ General

    private void renderGeneral(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int lw = (int) (w * 0.52);
        UiKit.panel(g, x, y, lw, h, UiKit.CARD, UiKit.BORDER);
        g.drawString(font, isSub() ? "Nom de la parcelle" : "Nom de la ville", x + 4, y + 5, UiKit.MUTED, false);
        button(g, x + lw - 70, y + 16, 66, 14, "Enregistrer", UiKit.ACCENT, perm("rename"), () -> {
            send("set_name", args("id", ci("id"), "name", boxValue("name")));
            clearDraft("name");
        }, perm("rename") ? null : "Permission cobblepulse.claims.city_name requise");
        int ly = y + 38;
        String clan = claim().has("clan") ? "[" + cs("clan") + "]" : "Sans clan";
        String[][] rows = {
                {"Propriétaire", cs("owner")},
                {"Clan", "ADMIN".equals(cs("type")) ? "—" : clan},
                {"Typologie", "ADMIN".equals(cs("type")) ? "Claim d'administration" : isSub() ? "Parcelle de « " + cs("parentName") + " »"
                        : claim().get("named").getAsBoolean() ? "Ville" : "Claim joueur"},
                {"Superficie", ci("area") + " blocs (" + (ci("maxX") - ci("minX") + 1) + " × " + (ci("maxZ") - ci("minZ") + 1) + ")"},
                {"Limites", "(" + ci("minX") + ", " + ci("minZ") + ") → (" + ci("maxX") + ", " + ci("maxZ") + ")"},
                {"Dimension", UiKit.dimName(cs("dim"))},
                {"Créé le", claim().has("created") && claim().get("created").getAsLong() > 0
                        ? new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(claim().get("created").getAsLong())) : "—"}};
        for (String[] r : rows) {
            g.drawString(font, r[0], x + 6, ly, UiKit.MUTED, false);
            g.drawString(font, trim(r[1], lw - 80), x + 74, ly, UiKit.TEXT, false);
            ly += 12;
        }
        if (data.has("ownerAvailable") && "OWNER".equals(data.get("role").getAsString())) {
            g.drawString(font, "Solde", x + 6, ly, UiKit.MUTED, false);
            g.drawString(font, data.get("ownerAvailable").getAsLong() + " blocs disponibles", x + 74, ly, UiKit.GOLD, false);
            ly += 12;
        }
        if (!cs("formerOwner").isEmpty()) {
            g.drawString(font, "Ancien propriétaire : " + cs("formerOwner"), x + 6, ly, UiKit.DIM, false);
            ly += 12;
        }
        int by = y + h - 18;
        int bx = x + 4;
        if (isSub()) {
            boolean inherit = claim().get("inherit").getAsBoolean();
            bx += autoButton(g, bx, by - 18, inherit ? "Hérite du claim : OUI" : "Hérite du claim : NON", inherit ? UiKit.OK : UiKit.WARN, true,
                    () -> send("set_inherit", args("id", ci("id"), "value", !inherit)), "Membres et permissions du claim principal appliqués à la parcelle") + 4;
            bx = x + 4;
        }
        if (perm("convert"))
            bx += autoButton(g, bx, by, armedLabel("convert", "Convertir en Claim Admin"), UiKit.VIOLET, true, () -> {
                if (confirm("convert")) send("convert_admin", args("id", ci("id")));
            }, "Staff : le territoire devient un claim d'administration (sans coût en blocs)") + 4;
        if (perm("toPlayer"))
            bx += autoButton(g, bx, by, armedLabel("toplayer", "Rendre au joueur"), UiKit.VIOLET, true, () -> {
                if (confirm("toplayer")) send("convert_player", args("id", ci("id")));
            }, cs("formerOwner").isEmpty() ? "Indiquez le joueur avec /claimadmin convertir joueur <pseudo>" : "Rendre à " + cs("formerOwner")) + 4;
        if (perm("delete"))
            autoButton(g, bx, by, armedLabel("delete", isSub() ? "Supprimer la parcelle" : "Abandonner"), UiKit.ERROR, true, () -> {
                if (confirm("delete")) send("delete", args("id", ci("id")));
            }, "Suppression définitive" + (isSub() ? "" : " (les blocs sont rendus)"));

        // apercu 2D
        int mxX = x + lw + 6, mw = w - lw - 6;
        UiKit.panel(g, mxX, y, mw, h, UiKit.CARD, UiKit.BORDER);
        g.drawString(font, "Aperçu 2D", mxX + 5, y + 5, UiKit.MUTED, false);
        boolean on = ClientState.forced3d == ci("id");
        button(g, mxX + mw - 92, y + 2, 88, 13, on ? "Rendu 3D : ON" : "Rendu 3D : OFF", on ? UiKit.OK : UiKit.MUTED, true,
                () -> ClientState.forced3d = on ? 0 : ci("id"), "Afficher les bordures de ce territoire dans le monde");
        renderMap(g, mx, my, mxX + 6, y + 20, mw - 12, h - 26);
    }

    private void renderMap(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        JsonObject map = data.getAsJsonObject("map");
        JsonObject top = map.getAsJsonObject("top");
        int minX = top.get("minX").getAsInt(), minZ = top.get("minZ").getAsInt(), maxX = top.get("maxX").getAsInt(), maxZ = top.get("maxZ").getAsInt();
        int margin = Math.max(8, Math.max(maxX - minX, maxZ - minZ) / 4);
        double vx1 = minX - margin, vz1 = minZ - margin, vx2 = maxX + 1 + margin, vz2 = maxZ + 1 + margin;
        double scale = Math.min(w / (vx2 - vx1), h / (vz2 - vz1));
        int ox = x + (int) ((w - (vx2 - vx1) * scale) / 2), oz = y + (int) ((h - (vz2 - vz1) * scale) / 2);
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, 0xFF0E1218);
        // grille de chunks
        for (int cx = (int) Math.floor(vx1 / 16) * 16; cx <= vx2; cx += 16) {
            int sx = ox + (int) ((cx - vx1) * scale);
            g.fill(sx, y, sx + 1, y + h, 0x18FFFFFF);
        }
        for (int cz = (int) Math.floor(vz1 / 16) * 16; cz <= vz2; cz += 16) {
            int sz = oz + (int) ((cz - vz1) * scale);
            g.fill(x, sz, x + w, sz + 1, 0x18FFFFFF);
        }
        for (JsonElement e : map.getAsJsonArray("near")) {
            JsonObject n = e.getAsJsonObject();
            int rgb = relColor(n.get("rel").getAsString());
            rect(g, n, vx1, vz1, scale, ox, oz, UiKit.withAlpha(rgb, 0x40), UiKit.withAlpha(rgb, 0xC0));
        }
        int rgb = relColor(cs("rel"));
        rect(g, top, vx1, vz1, scale, ox, oz, UiKit.withAlpha(rgb, 0x55), UiKit.withAlpha(rgb, 0xFF));
        for (JsonElement e : data.getAsJsonArray("subs")) {
            JsonObject s = e.getAsJsonObject();
            boolean cur = s.get("id").getAsInt() == ci("id");
            rect(g, s, vx1, vz1, scale, ox, oz, cur ? 0x55FFFFFF : 0x22FFFFFF, cur ? 0xFFFFFFFF : 0xAAFFFFFF);
        }
        if (map.get("sameDim").getAsBoolean()) {
            double pxw = map.get("px").getAsDouble(), pzw = map.get("pz").getAsDouble();
            if (minecraft.player != null) {
                pxw = minecraft.player.getX();
                pzw = minecraft.player.getZ();
            }
            int sx = ox + (int) ((pxw - vx1) * scale), sz = oz + (int) ((pzw - vz1) * scale);
            g.fill(sx - 2, sz - 2, sx + 3, sz + 3, 0xFF000000);
            g.fill(sx - 1, sz - 1, sx + 2, sz + 2, UiKit.GOLD);
            tooltip(sx - 3, sz - 3, 7, 7, List.of(Component.literal("Vous")));
        }
        g.disableScissor();
        g.drawString(font, "N", x + w / 2 - 2, y + 2, UiKit.DIM, false);
    }

    private void rect(GuiGraphics g, JsonObject r, double vx1, double vz1, double scale, int ox, int oz, int fill, int line) {
        int x1 = ox + (int) ((r.get("minX").getAsInt() - vx1) * scale), z1 = oz + (int) ((r.get("minZ").getAsInt() - vz1) * scale);
        int x2 = ox + (int) ((r.get("maxX").getAsInt() + 1 - vx1) * scale), z2 = oz + (int) ((r.get("maxZ").getAsInt() + 1 - vz1) * scale);
        if (x2 <= x1) x2 = x1 + 1;
        if (z2 <= z1) z2 = z1 + 1;
        g.fill(x1, z1, x2, z2, fill);
        g.fill(x1, z1, x2, z1 + 1, line);
        g.fill(x1, z2 - 1, x2, z2, line);
        g.fill(x1, z1, x1 + 1, z2, line);
        g.fill(x2 - 1, z1, x2, z2, line);
        tooltip(x1, z1, x2 - x1, z2 - z1, List.of(Component.literal(r.get("name").getAsString())));
    }

    static int relColor(String rel) {
        return switch (rel == null ? "" : rel) {
            case "OWN" -> 0x3BE36B;
            case "TRUSTED" -> 0x34D8E8;
            case "CLAN" -> 0x3D7BFF;
            case "ADMIN" -> 0xB45CFF;
            default -> 0xFF8C2E;
        };
    }

    // ------------------------------------------------------------------ Permissions

    private void renderPerms(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        String[][] cats = {{"ALL", "Tous", "Toutes les catégories"}, {"A", "A", "Construction & Blocs"}, {"B", "B", "Conteneurs & Interactions"},
                {"C", "C", "Entités & Objets"}, {"D", "D", "Environnement & Déplacement"}, {"E", "E", "Cobblemon"}, {"F", "F", "Admin & Villes"}};
        int cx = x + 156;
        for (String[] c : cats) {
            boolean on = c[0].equals(category);
            cx += button(g, cx, y, c[0].equals("ALL") ? 30 : 18, 14, c[1], on ? UiKit.ACCENT : UiKit.MUTED, true, () -> {
                category = c[0];
                scroll.put("perms", 0);
            }, c[2]) + 2;
        }
        int colV = x + w - 160, colM = x + w - 82;
        int hy = y + 18;
        g.drawString(font, "Permission", x + 4, hy, UiKit.DIM, false);
        g.drawString(font, "Visiteurs", colV + 8, hy, UiKit.DIM, false);
        g.drawString(font, "Membres", colM + 8, hy, UiKit.DIM, false);
        int listY = hy + 11, listH = h - (listY - y);
        String q = UiKit.norm(boxValue("search"));
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement e : data.getAsJsonArray("flags")) {
            JsonObject f = e.getAsJsonObject();
            if (!category.equals("ALL") && !category.equals(f.get("cat").getAsString())) continue;
            if (!q.isEmpty() && !UiKit.norm(f.get("label").getAsString() + " " + f.get("id").getAsString() + " " + f.get("desc").getAsString()).contains(q))
                continue;
            rows.add(f);
        }
        int rowH = 16;
        int s = scrollArea("perms", x, listY, w, listH, rows.size() * rowH);
        g.enableScissor(x, listY, x + w, listY + listH);
        String lastCat = null;
        for (int i = 0; i < rows.size(); i++) {
            JsonObject f = rows.get(i);
            int ry = listY + i * rowH - s;
            if (ry + rowH < listY || ry > listY + listH) continue;
            boolean editable = f.get("editable").getAsBoolean();
            boolean locked = f.get("locked").getAsBoolean();
            g.fill(x, ry, x + w - 4, ry + rowH - 1, UiKit.isHover(mx, my, x, ry, w - 4, rowH - 1) ? UiKit.CARD_HOVER : UiKit.CARD);
            String cat = f.get("cat").getAsString();
            g.drawString(font, cat, x + 3, ry + 4, UiKit.DIM, false);
            int lx = x + 12;
            if (locked) {
                g.drawString(font, "🔒", lx, ry + 4, UiKit.GOLD, false);
                lx += 10;
            }
            g.drawString(font, trim(f.get("label").getAsString(), colV - lx - 22), lx, ry + 4, editable ? UiKit.TEXT : UiKit.MUTED, false);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(f.get("label").getAsString()).withStyle(s2 -> s2.withColor(0xFFD24A)));
            tip.add(Component.literal(f.get("id").getAsString()).withStyle(s2 -> s2.withColor(0x5C6878)));
            tip.add(Component.literal(f.get("desc").getAsString()));
            tip.add(Component.literal(f.get("catLabel").getAsString()).withStyle(s2 -> s2.withColor(0x8E9AAB)));
            if (!f.get("zone").getAsBoolean()) tip.add(Component.literal("Compte comme membre dès le niveau : " + f.get("level").getAsString()).withStyle(s2 -> s2.withColor(0x8E9AAB)));
            if (locked) tip.add(Component.literal("🔒 Verrouillée par le serveur").withStyle(s2 -> s2.withColor(0xFFA94D)));
            else if (!editable) tip.add(Component.literal("Lecture seule").withStyle(s2 -> s2.withColor(0xFFA94D)));
            tooltip(x, ry, colV - x - 18, rowH, tip);
            String id = f.get("id").getAsString();
            if (id.equals("command.execute")) {
                button(g, colV - 18, ry + 1, 14, 13, "⚙", UiKit.ACCENT, true, this::openCommands, "Gestionnaire granulaire des commandes");
            }
            if (f.get("zone").getAsBoolean()) {
                selector(g, colV, ry + 1, colM + 76 - colV, f, "v", "dv", editable, "visitor");
            } else {
                selector(g, colV, ry + 1, 74, f, "v", "dv", editable, "visitor");
                selector(g, colM, ry + 1, 74, f, "m", "dm", editable, "member");
            }
        }
        g.disableScissor();
        scrollbar(g, "perms", x + w - 3, listY, listH, rows.size() * rowH);
        if (rows.isEmpty()) g.drawString(font, "Aucune permission ne correspond.", x + 6, listY + 6, UiKit.MUTED, false);
    }

    private void selector(GuiGraphics g, int x, int y, int w, JsonObject f, String key, String defKey, boolean editable, String column) {
        String state = f.get(key).getAsString();
        String def = f.get(defKey).getAsString();
        String label = state.equals("DEFAULT") ? "◌ Défaut " + (def.equals("ALLOW") ? "(✔)" : "(✖)") : UiKit.stateLabel(state);
        int color = state.equals("DEFAULT") ? (def.equals("ALLOW") ? 0xFF4E8F5A : 0xFF9A5050) : UiKit.stateColor(state);
        boolean hover = editable && UiKit.isHover(mouseX, mouseY, x, y, w, 13);
        g.fill(x, y, x + w, y + 13, hover ? UiKit.CARD_ON : 0xFF141922);
        g.fill(x, y + 12, x + w, y + 13, editable ? UiKit.withAlpha(color, 0xA0) : UiKit.BORDER);
        String t = trim(label, w - 4);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + 3, editable ? color : UiKit.DIM, false);
        String id = f.get("id").getAsString();
        String next = switch (state) {
            case "ALLOW" -> "DENY";
            case "DENY" -> "DEFAULT";
            default -> "ALLOW";
        };
        String prev = switch (state) {
            case "ALLOW" -> "DEFAULT";
            case "DENY" -> "ALLOW";
            default -> "DENY";
        };
        List<Component> tip = List.of(Component.literal("Clic : " + UiKit.stateLabel(next) + " · Clic droit : " + UiKit.stateLabel(prev)),
                Component.literal("Défaut ici : " + UiKit.stateLabel(def)).withStyle(s -> s.withColor(0x8E9AAB)));
        hit(x, y, w, 13, editable ? () -> send("set_flag", args("id", ci("id"), "flag", id, "column", column, "state", next)) : null,
                editable ? () -> send("set_flag", args("id", ci("id"), "flag", id, "column", column, "state", prev)) : null, editable ? tip : null);
    }

    private void openCommands() {
        loadCommands();
        cmdOpen = true;
        saveDrafts();
        rebuildWidgets();
    }

    private void loadCommands() {
        JsonObject c = data.getAsJsonObject("commands");
        String mode = c.get("mode").getAsString();
        String v = c.get("v").getAsString();
        cmdMode = mode.equals("FLAG") ? (v.equals("DENY") ? "DENY" : v.equals("ALLOW") ? "ALLOW" : "DEFAULT") : mode;
        cmdList.clear();
        for (JsonElement e : c.getAsJsonArray("list")) cmdList.add(e.getAsString());
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mx, int my, float pt) {
        if (!cmdOpen || !"perms".equals(tab)) return;
        hit(0, 0, width, height, () -> {
        }, () -> {
        }, null);
        int w = 300, h = 210;
        int x = px + (pw - w) / 2, y = py + (ph - h) / 2;
        g.fill(0, 0, width, height, 0x66000000);
        UiKit.panel(g, x, y, w, h, 0xF8161C26, UiKit.ACCENT);
        g.drawString(font, "Commandes dans le territoire (command.execute)", x + 8, y + 7, UiKit.GOLD, false);
        String[][] modes = {{"ALLOW", "Tout autoriser"}, {"DENY", "Tout bloquer"}, {"BLACKLIST", "Liste noire"}, {"WHITELIST", "Liste blanche"},
                {"DEFAULT", "Défaut"}};
        int bx = x + 8;
        for (String[] m : modes) {
            boolean on = m[0].equals(cmdMode);
            bx += autoButton(g, bx, y + 22, m[1], on ? UiKit.ACCENT : UiKit.MUTED, true, () -> cmdMode = m[0], switch (m[0]) {
                case "ALLOW" -> "Toutes les commandes sont utilisables dans le claim";
                case "DENY" -> "Aucune commande (sauf celles du mod de claim)";
                case "BLACKLIST" -> "Les préfixes listés sont bloqués";
                case "WHITELIST" -> "Seuls les préfixes listés sont autorisés";
                default -> "Valeur système de la matrice";
            }) + 3;
        }
        boolean listMode = cmdMode.equals("BLACKLIST") || cmdMode.equals("WHITELIST");
        g.drawString(font, listMode ? (cmdMode.equals("BLACKLIST") ? "Préfixes bloqués :" : "Préfixes autorisés :") : "La liste ne s'applique qu'en mode liste.",
                x + 8, y + 42, listMode ? UiKit.TEXT : UiKit.DIM, false);
        int ly = y + 54, lh = 96;
        g.fill(x + 8, ly, x + w - 8, ly + lh, 0xFF0E1218);
        int s = scrollArea("cmd", x + 8, ly, w - 16, lh, cmdList.size() * 13);
        g.enableScissor(x + 8, ly, x + w - 8, ly + lh);
        for (int i = 0; i < cmdList.size(); i++) {
            String c = cmdList.get(i);
            int ry = ly + 2 + i * 13 - s;
            g.drawString(font, "/" + c, x + 12, ry + 2, listMode ? UiKit.TEXT : UiKit.DIM, false);
            int idx = i;
            button(g, x + w - 26, ry, 14, 12, "✖", UiKit.ERROR, true, () -> cmdList.remove(idx), "Retirer");
        }
        g.disableScissor();
        if (cmdList.isEmpty()) g.drawString(font, "(vide) — ex : sethome, tpa, tpaccept, back, fly, enderchest", x + 12, ly + 4, UiKit.DIM, false);
        button(g, x + 162, y + h - 40, 56, 14, "Ajouter", UiKit.ACCENT, true, this::addCommand, null);
        button(g, x + 8, y + h - 20, 90, 14, "Appliquer", UiKit.OK, true, () -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (String c : cmdList) arr.add(c);
            send("set_commands", args("id", ci("id"), "mode", cmdMode, "list", arr));
            cmdOpen = false;
            saveDrafts();
            rebuildWidgets();
        }, null);
        button(g, x + w - 98, y + h - 20, 90, 14, "Fermer", UiKit.MUTED, true, () -> {
            cmdOpen = false;
            saveDrafts();
            rebuildWidgets();
        }, null);
    }

    private void addCommand() {
        String v = boxValue("cmdEntry").trim().toLowerCase();
        while (v.startsWith("/")) v = v.substring(1);
        if (!v.isEmpty() && !cmdList.contains(v) && cmdList.size() < 64) cmdList.add(v);
        EditBox b = boxes.get("cmdEntry");
        if (b != null) b.setValue("");
        clearDraft("cmdEntry");
    }

    @Override
    protected void onEnter(EditBox b) {
        if (b == boxes.get("cmdEntry")) addCommand();
        else if (b == boxes.get("member")) addMember();
        else if (b == boxes.get("name")) {
            send("set_name", args("id", ci("id"), "name", b.getValue()));
            clearDraft("name");
        }
    }

    // ------------------------------------------------------------------ Membres

    private void addMember() {
        String name = boxValue("member").trim();
        if (name.isEmpty()) return;
        send("trust", args("id", ci("id"), "name", name, "level", newLevel));
        EditBox b = boxes.get("member");
        if (b != null) b.setValue("");
        clearDraft("member");
    }

    private void renderMembers(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        g.drawString(font, "Ajouter un joueur (équivaut à /trust) :", x, y + 2, UiKit.MUTED, false);
        int bx = x + 146;
        for (String[] l : LEVELS) {
            boolean on = l[0].equals(newLevel);
            boolean allowed = !l[0].equals("MANAGER") || perm("grantManager");
            bx += button(g, bx, y + 14, 62, 14, l[1], on ? UiKit.ACCENT : UiKit.MUTED, allowed, () -> newLevel = l[0], l[2]) + 2;
        }
        button(g, bx + 2, y + 14, w - (bx + 2 - x), 14, "Ajouter", UiKit.OK, perm("trust"), this::addMember, null);
        int listY = y + 36, listH = h - 36;
        JsonArray members = data.getAsJsonArray("members");
        JsonArray inherited = data.has("inheritedMembers") ? data.getAsJsonArray("inheritedMembers") : new JsonArray();
        int rowH = 18;
        int total = members.size() * rowH + (inherited.isEmpty() ? 0 : 14 + inherited.size() * 12);
        int s = scrollArea("members", x, listY, w, listH, total);
        g.enableScissor(x, listY, x + w, listY + listH);
        int ry = listY - s;
        if (members.isEmpty()) g.drawString(font, "Aucun membre : seuls vous et le staff avez accès.", x + 4, ry + 4, UiKit.DIM, false);
        for (JsonElement e : members) {
            JsonObject m = e.getAsJsonObject();
            String uuid = m.get("uuid").getAsString();
            String lvl = m.get("level").getAsString();
            g.fill(x, ry, x + w - 4, ry + rowH - 2, UiKit.CARD);
            boolean pub = uuid.equals("*");
            g.drawString(font, pub ? "Tout le monde (public)" : m.get("name").getAsString(), x + 6, ry + 5, pub ? UiKit.GOLD : UiKit.TEXT, false);
            int levelIdx = indexOf(lvl);
            String next = LEVELS[(levelIdx + 1) % LEVELS.length][0];
            String prev = LEVELS[(levelIdx + LEVELS.length - 1) % LEVELS.length][0];
            if (pub && next.equals("MANAGER")) next = "ACCESS";
            if (pub && prev.equals("MANAGER")) prev = "BUILD";
            String fnext = next, fprev = prev;
            String name = m.get("name").getAsString();
            button(g, x + w - 168, ry + 1, 90, 14, m.get("label").getAsString(), levelColor(lvl), perm("trust"), null, null);
            hit(x + w - 168, ry + 1, 90, 14, () -> send("trust", args("id", ci("id"), "name", pub ? "public" : name, "level", fnext)),
                    () -> send("trust", args("id", ci("id"), "name", pub ? "public" : name, "level", fprev)),
                    List.of(Component.literal("Clic : " + label(fnext) + " · Clic droit : " + label(fprev)), Component.literal(LEVELS[Math.max(0, levelIdx)][2])));
            button(g, x + w - 74, ry + 1, 66, 14, armedLabel("untrust" + uuid, "Retirer"), UiKit.ERROR, perm("trust"), () -> {
                if (confirm("untrust" + uuid)) send("untrust", args("id", ci("id"), "uuid", uuid));
            }, "Équivaut à /untrust");
            ry += rowH;
        }
        if (!inherited.isEmpty()) {
            g.drawString(font, "Hérités du claim principal :", x + 4, ry + 3, UiKit.DIM, false);
            ry += 14;
            for (JsonElement e : inherited) {
                JsonObject m = e.getAsJsonObject();
                g.drawString(font, "  " + m.get("name").getAsString() + " — " + m.get("label").getAsString(), x + 4, ry + 1, UiKit.MUTED, false);
                ry += 12;
            }
        }
        g.disableScissor();
        scrollbar(g, "members", x + w - 3, listY, listH, total);
    }

    private static int indexOf(String lvl) {
        for (int i = 0; i < LEVELS.length; i++) if (LEVELS[i][0].equals(lvl)) return i;
        return 2;
    }

    private static String label(String lvl) {
        for (String[] l : LEVELS) if (l[0].equals(lvl)) return l[1];
        return lvl;
    }

    private static int levelColor(String lvl) {
        return switch (lvl) {
            case "ACCESS" -> 0xFF8EC8FF;
            case "CONTAINER" -> 0xFF5FD3F3;
            case "BUILD" -> UiKit.OK;
            case "MANAGER" -> UiKit.GOLD;
            default -> UiKit.MUTED;
        };
    }

    // ------------------------------------------------------------------ Parcelles

    private void renderSubs(GuiGraphics g, int mx, int my, int x, int y, int w, int h) {
        int ty = y + 2;
        if (isSub()) {
            autoButton(g, x, y, "← Claim principal", UiKit.ACCENT, true, () -> send("open_manager", args("id", data.get("topId").getAsInt())), null);
            ty = y + 18;
        }
        g.drawString(font, perm("subclaim") ? "Créer une parcelle : avec la Balise, posez les deux coins à l'intérieur du territoire."
                : "Permission cobblepulse.claims.subclaim requise pour créer des parcelles.", x, ty, UiKit.MUTED, false);
        g.drawString(font, "Chaque parcelle a ses propres membres (location, boutique, habitation).", x, ty + 11, UiKit.DIM, false);
        int listY = ty + 26, listH = h - (listY - y);
        JsonArray subs = data.getAsJsonArray("subs");
        int rowH = 20;
        int s = scrollArea("subs", x, listY, w, listH, subs.size() * rowH);
        g.enableScissor(x, listY, x + w, listY + listH);
        for (int i = 0; i < subs.size(); i++) {
            JsonObject so = subs.get(i).getAsJsonObject();
            int ry = listY + i * rowH - s;
            int sid = so.get("id").getAsInt();
            boolean cur = sid == ci("id");
            g.fill(x, ry, x + w - 4, ry + rowH - 2, cur ? UiKit.CARD_ON : UiKit.CARD);
            g.drawString(font, trim(so.get("name").getAsString(), 150), x + 6, ry + 2, UiKit.TEXT, false);
            int sw = so.get("maxX").getAsInt() - so.get("minX").getAsInt() + 1, sl = so.get("maxZ").getAsInt() - so.get("minZ").getAsInt() + 1;
            g.drawString(font, sw + " × " + sl + " · " + so.get("members").getAsInt() + " membre(s)" + (so.get("inherit").getAsBoolean() ? " · hérite" : ""),
                    x + 6, ry + 10, UiKit.DIM, false);
            if (!cur) button(g, x + w - 140, ry + 2, 64, 14, "Gérer", UiKit.ACCENT, true, () -> send("open_manager", args("id", sid)), null);
            button(g, x + w - 72, ry + 2, 64, 14, armedLabel("sub" + sid, "Supprimer"), UiKit.ERROR, true, () -> {
                if (confirm("sub" + sid)) send("delete", args("id", sid));
            }, null);
        }
        g.disableScissor();
        scrollbar(g, "subs", x + w - 3, listY, listH, subs.size() * rowH);
        if (subs.isEmpty()) g.drawString(font, "Aucune parcelle pour l'instant.", x + 6, listY + 6, UiKit.DIM, false);
    }

    // ------------------------------------------------------------------ Reglages admin

    private void renderSettings(GuiGraphics g, int x, int y, int w, int h) {
        JsonObject st = data.getAsJsonObject("settings");
        g.drawString(font, "Ces réglages s'appliquent quand les permissions admin correspondantes sont sur Autorisé.", x, y + 2, UiKit.MUTED, false);
        g.drawString(font, "Météo figée", x, y + 22, UiKit.TEXT, false);
        String weather = st.get("weather").getAsString();
        int bx = x + 110;
        for (String[] wv : new String[][]{{"CLEAR", "Clair"}, {"RAIN", "Pluie"}, {"THUNDER", "Orage"}}) {
            bx += autoButton(g, bx, y + 19, wv[1], wv[0].equals(weather) ? UiKit.ACCENT : UiKit.MUTED, true,
                    () -> send("set_setting", args("id", ci("id"), "key", "weather", "value", wv[0])), "admin.weather_lock") + 3;
        }
        g.drawString(font, "Heure figée (ticks)", x, y + 44, UiKit.TEXT, false);
        bx = x + 174;
        for (String[] tv : new String[][]{{"1000", "Matin"}, {"6000", "Midi"}, {"12500", "Soir"}, {"18000", "Nuit"}}) {
            bx += autoButton(g, bx, y + 40, tv[1], UiKit.MUTED, true, () -> {
                send("set_setting", args("id", ci("id"), "key", "time", "value", tv[0]));
                clearDraft("time");
            }, "admin.time_lock : " + tv[0]) + 3;
        }
        button(g, bx + 4, y + 40, 70, 14, "Appliquer", UiKit.OK, true, () -> send("set_setting", args("id", ci("id"), "key", "time", "value", boxValue("time"))), null);
        g.drawString(font, "Plafond de niveau", x, y + 86, UiKit.TEXT, false);
        button(g, x + 174, y + 82, 70, 14, "Appliquer", UiKit.OK, true, () -> send("set_setting", args("id", ci("id"), "key", "level_cap", "value", boxValue("cap"))),
                "admin.level_cap : niveau imposé en combat");
        g.drawString(font, "Arène : admin.pvp_arena soigne les équipes avant et après chaque combat.", x, y + 110, UiKit.DIM, false);
    }

    /** Tests : saisie du nom de ville puis Entree, comme un joueur. */
    public void testTypeName(String name) {
        EditBox b = boxes.get("name");
        if (b == null) return;
        setFocused(b);
        b.setFocused(true);
        b.setValue(name);
        keyPressed(257, 0, 0);
    }

    /** Tests : bascule le sous-panneau des commandes. */
    public void testOpenCommands() {
        openCommands();
    }

    public boolean commandsOpen() {
        return cmdOpen;
    }

    public void testSetSearch(String q) {
        EditBox b = boxes.get("search");
        if (b != null) b.setValue(q);
    }

    public void testSetCategory(String c) {
        category = c;
    }
}
