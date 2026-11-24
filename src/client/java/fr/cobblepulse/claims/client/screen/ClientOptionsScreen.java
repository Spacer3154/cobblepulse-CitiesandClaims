package fr.cobblepulse.claims.client.screen;

import fr.cobblepulse.claims.client.ClientConfig;
import fr.cobblepulse.claims.client.ClientState;
import fr.cobblepulse.claims.client.map.MapBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Options client du mod : bascule rapide du rendu sur les cartes et du rendu 3D. */
public class ClientOptionsScreen extends BaseScreen {
    private final Screen parent;

    public ClientOptionsScreen(Screen parent) {
        super("Options — Cités & Territoires");
        this.parent = parent;
        this.maxW = 330;
        this.maxH = 210;
    }

    @Override
    public void onClose() {
        ClientConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    protected String footer() {
        return "Cartes détectées : " + MapBridge.detected();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt, int x, int y, int w, int h) {
        ClientConfig c = ClientConfig.get();
        int yy = y + 4;
        yy = toggle(g, x, yy, w, "Territoires sur les cartes", "JourneyMap, Xaero's Minimap, Xaero's World Map", c.map_overlays, () -> {
            c.map_overlays = !c.map_overlays;
            ClientState.version++;
        });
        yy = toggle(g, x, yy, w, "WorldBorder sur les cartes", "Tracé continu de la limite de la dimension", c.map_worldborder, () -> {
            c.map_worldborder = !c.map_worldborder;
            ClientState.version++;
        });
        yy = toggle(g, x, yy, w, "Noms des villes sur les cartes", "Étiquettes JourneyMap", c.map_labels, () -> {
            c.map_labels = !c.map_labels;
            ClientState.version++;
        });
        yy = toggle(g, x, yy, w, "Bordures 3D permanentes", "Sinon : seulement avec la Balise ou la Lunette en main", c.always_render_3d,
                () -> c.always_render_3d = !c.always_render_3d);
        yy = toggle(g, x, yy, w, "WorldBorder translucide en 3D", "Mur visible à l'approche de la limite", c.render_worldborder,
                () -> c.render_worldborder = !c.render_worldborder);
        g.drawString(font, "Distance de rendu 3D : " + c.render_radius + " blocs", x + 4, yy + 4, UiKit.TEXT, false);
        button(g, x + w - 60, yy, 26, 14, "−", UiKit.ACCENT, c.render_radius > 32, () -> c.render_radius = Math.max(32, c.render_radius - 32), null);
        button(g, x + w - 30, yy, 26, 14, "+", UiKit.ACCENT, c.render_radius < 512, () -> c.render_radius = Math.min(512, c.render_radius + 32), null);
        button(g, x + w - 70, y + h - 14, 70, 14, "Terminé", UiKit.OK, true, this::onClose, null);
    }

    private int toggle(GuiGraphics g, int x, int y, int w, String label, String tip, boolean on, Runnable flip) {
        g.fill(x, y, x + w, y + 22, UiKit.CARD);
        g.drawString(font, label, x + 6, y + 3, UiKit.TEXT, false);
        g.drawString(font, trim(tip, w - 80), x + 6, y + 12, UiKit.DIM, false);
        button(g, x + w - 60, y + 4, 56, 14, on ? "Activé" : "Désactivé", on ? UiKit.OK : UiKit.MUTED, true, () -> {
            flip.run();
            ClientConfig.save();
        }, null);
        return y + 25;
    }
}
