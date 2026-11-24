package fr.cobblepulse.claims.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/** Fiche d'information synthetique en lecture seule (claim d'un tiers). */
public class ClaimInfoScreen extends BaseScreen {
    private final JsonObject data;

    public ClaimInfoScreen(JsonObject data) {
        super("Fiche du territoire");
        this.data = data;
        this.maxW = 340;
        this.maxH = 250;
    }

    private JsonObject claim() {
        return data.getAsJsonObject("claim");
    }

    private String cs(String k) {
        return claim().has(k) && !claim().get(k).isJsonNull() ? claim().get(k).getAsString() : "";
    }

    @Override
    protected String footer() {
        return "Lecture seule · K : fermer";
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        int rgb = ClaimManagerScreen.relColor(cs("rel"));
        UiKit.panel(g, x, y, w, 52, UiKit.CARD, UiKit.withAlpha(rgb, 0xFF));
        g.fill(x, y, x + 3, y + 52, UiKit.withAlpha(rgb, 0xFF));
        g.drawString(font, trim(data.get("topName").getAsString(), w - 12), x + 8, y + 5, UiKit.withAlpha(rgb, 0xFF), false);
        boolean admin = "ADMIN".equals(cs("type"));
        String clan = claim().has("clan") ? "[" + cs("clan") + "]" : "Sans clan";
        g.drawString(font, admin ? "Zone Administrative" : "Propriétaire : " + cs("owner") + "  ·  Clan : " + clan, x + 8, y + 17, UiKit.TEXT, false);
        int cw = claim().get("maxX").getAsInt() - claim().get("minX").getAsInt() + 1, cl = claim().get("maxZ").getAsInt() - claim().get("minZ").getAsInt() + 1;
        g.drawString(font, cs("area") + " blocs (" + cw + " × " + cl + ") · " + UiKit.dimName(cs("dim")) + (claim().get("parent").getAsInt() != 0 ? " · parcelle « " + cs("name") + " »" : ""),
                x + 8, y + 28, UiKit.MUTED, false);
        g.drawString(font, "Votre rôle : " + data.get("role").getAsString(), x + 8, y + 39, UiKit.ACCENT, false);
        int ry = y + 58;
        g.drawString(font, "Règles d'accès", x, ry, UiKit.GOLD, false);
        g.drawString(font, "Vous", x + w - 92, ry, UiKit.DIM, false);
        g.drawString(font, "Public", x + w - 46, ry, UiKit.DIM, false);
        ry += 11;
        int listH = h - (ry - y) - 18;
        int rowH = 12;
        var rules = data.getAsJsonArray("rules");
        int s = scrollArea("rules", x, ry, w, listH, rules.size() * rowH);
        g.enableScissor(x, ry, x + w, ry + listH);
        int i = 0;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            int yy = ry + i * rowH - s;
            g.drawString(font, r.get("label").getAsString(), x + 4, yy + 2, UiKit.TEXT, false);
            boolean you = r.get("you").getAsBoolean(), pub = r.get("public").getAsBoolean();
            g.drawString(font, you ? "✔" : "✖", x + w - 84, yy + 2, you ? UiKit.OK : UiKit.ERROR, false);
            g.drawString(font, pub ? "✔" : "✖", x + w - 38, yy + 2, pub ? UiKit.OK : UiKit.ERROR, false);
            i++;
        }
        g.disableScissor();
        scrollbar(g, "rules", x + w - 3, ry, listH, rules.size() * rowH);
        int by = y + h - 14;
        if (data.get("canManage").getAsBoolean())
            autoButton(g, x, by, "Gérer", UiKit.ACCENT, true, () -> send("open_manager", args("id", claim().get("id").getAsInt())), null);
        button(g, x + w - 70, by, 70, 14, "Fermer", UiKit.MUTED, true, this::onClose, null);
    }
}
