package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.client.ClientConfig;
import fr.cobblepulse.claims.client.ClientState;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * JourneyMap (API v2) : un PolygonOverlay par territoire (couleur par relation, infobulle au survol : nom, proprietaire,
 * tag de clan) et le trace de la WorldBorder, sur la minimap et la carte plein ecran. Un territoire n'apparait que sur
 * les chunks deja decouverts par le joueur (decoupe au chunk, avec trous si besoin).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapClaimsPlugin implements IClientPlugin {
    private static IClientAPI api;
    public static volatile int shown;
    /** Territoires non dessines (aucun chunk decouvert) / dessines en partie. */
    public static volatile int hidden, partial;

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        CobblePulseClaims.LOG.info("[CobblePulse-Claims] Connecteur JourneyMap actif");
        refresh();
    }

    @Override
    public String getModId() {
        return CobblePulseClaims.MOD_ID;
    }

    public static boolean ready() {
        return api != null;
    }

    public static synchronized void refresh() {
        if (api == null) return;
        api.removeAll(CobblePulseClaims.MOD_ID);
        shown = 0;
        hidden = 0;
        partial = 0;
        if (!ClientConfig.get().map_overlays || ClientState.dim == null || ClientState.dim.isEmpty()) return;
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ClientState.dim));
        List<ClientState.CClaim> claims = new ArrayList<>(ClientState.CLAIMS.values());
        claims.sort((a, b) -> Boolean.compare(a.isSub(), b.isSub()));
        for (ClientState.CClaim c : claims) {
            // decouverte : seule la partie deja exploree par le joueur est dessinee
            List<DiscoveredShape.Piece> pieces = DiscoveredShape.visible(c.minX, c.minZ, c.maxX, c.maxZ, Discovery::visible);
            if (pieces.isEmpty()) {
                hidden++;
                continue;
            }
            boolean whole = pieces.size() == 1 && pieces.get(0).holes().isEmpty()
                    && pieces.get(0).area() == (double) (c.maxX - c.minX + 1) * (c.maxZ - c.minZ + 1);
            if (!whole) partial++;
            DiscoveredShape.Piece largest = pieces.get(0);
            for (DiscoveredShape.Piece pc : pieces) if (pc.area() > largest.area()) largest = pc;
            int rgb = c.isSub() ? 0xFFFFFF : c.rgb();
            for (DiscoveredShape.Piece pc : pieces) {
                ShapeProperties sp = new ShapeProperties().setStrokeColor(rgb).setStrokeOpacity(0.95f).setStrokeWidth(c.isSub() ? 1.5f : 2.5f)
                        .setFillColor(rgb).setFillOpacity(c.isSub() ? 0.08f : 0.28f);
                List<MapPolygon> holes = new ArrayList<>();
                for (DiscoveredShape.Loop h : pc.holes()) holes.add(polygon(h));
                PolygonOverlay o = holes.isEmpty() ? new PolygonOverlay(CobblePulseClaims.MOD_ID, dim, sp, polygon(pc.outer()))
                        : new PolygonOverlay(CobblePulseClaims.MOD_ID, dim, sp, polygon(pc.outer()), holes);
                o.setOverlayGroupName("CobblePulse — Territoires");
                o.setTitle(String.join("\n", c.tooltip()));
                if (pc == largest && ClientConfig.get().map_labels && !c.isSub()) {
                    o.setLabel(c.name);
                    o.setTextProperties(new TextProperties().setColor(rgb).setScale(1.2f).setBackgroundOpacity(0.4f).setOpacity(1f)
                            .setActiveUIs(Context.UI.Fullscreen).setMinZoom(1));
                }
                show(o);
            }
        }
        ClientState.CBorder b = ClientState.border;
        if (b != null && b.enabled && ClientConfig.get().map_worldborder) {
            List<BlockPos> pts = new ArrayList<>();
            if (b.circle) {
                int n = 128;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    pts.add(BlockPos.containing(b.cx + Math.cos(a) * b.radius, 64, b.cz + Math.sin(a) * b.radius));
                }
            } else {
                pts.add(BlockPos.containing(b.cx - b.sx / 2, 64, b.cz - b.sz / 2));
                pts.add(BlockPos.containing(b.cx + b.sx / 2, 64, b.cz - b.sz / 2));
                pts.add(BlockPos.containing(b.cx + b.sx / 2, 64, b.cz + b.sz / 2));
                pts.add(BlockPos.containing(b.cx - b.sx / 2, 64, b.cz + b.sz / 2));
            }
            ShapeProperties sp = new ShapeProperties().setStrokeColor(MapStyle.BORDER_RGB).setStrokeOpacity(0.9f).setStrokeWidth(3f)
                    .setFillColor(MapStyle.BORDER_RGB).setFillOpacity(0f);
            PolygonOverlay o = new PolygonOverlay(CobblePulseClaims.MOD_ID, dim, sp, new MapPolygon(pts));
            o.setOverlayGroupName("CobblePulse — WorldBorder");
            o.setTitle("Limite du monde");
            show(o);
        }
    }

    private static MapPolygon polygon(DiscoveredShape.Loop l) {
        List<BlockPos> pts = new ArrayList<>(l.xs().length);
        for (int k = 0; k < l.xs().length; k++) pts.add(new BlockPos(l.xs()[k], 64, l.zs()[k]));
        return new MapPolygon(pts);
    }

    private static void show(PolygonOverlay o) {
        try {
            api.show(o);
            shown++;
        } catch (Exception e) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] JourneyMap : overlay refusé : {}", e.toString());
        }
    }
}
