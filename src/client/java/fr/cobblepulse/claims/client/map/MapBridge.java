package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.CobblePulseClaims;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Connecteurs cartographiques optionnels. Chaque connecteur vit dans sa propre classe, chargee seulement si le mod de
 * carte est present : le mod fonctionne avec JourneyMap, les cartes de Xaero, les trois ou aucun.
 */
public final class MapBridge {
    public static final boolean JOURNEYMAP = FabricLoader.getInstance().isModLoaded("journeymap");
    public static final boolean XAERO_WORLDMAP = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
    public static final boolean XAERO_MINIMAP = FabricLoader.getInstance().isModLoaded("xaerominimap");

    private MapBridge() {}

    public static void init() {
        if (JOURNEYMAP || XAERO_WORLDMAP || XAERO_MINIMAP) CobblePulseClaims.LOG.info("[CobblePulse-Claims] Cartes détectées : {}", detected());
    }

    public static String detected() {
        List<String> l = new ArrayList<>();
        if (JOURNEYMAP) l.add("JourneyMap");
        if (XAERO_MINIMAP) l.add("Xaero's Minimap");
        if (XAERO_WORLDMAP) l.add("Xaero's World Map");
        return l.isEmpty() ? "aucune" : String.join(", ", l);
    }

    /** Territoires ou bordure modifies : chaque carte presente est rafraichie. */
    public static void refresh() {
        try {
            if (JOURNEYMAP) JourneyMapClaimsPlugin.refresh();
        } catch (Throwable t) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] JourneyMap : rafraîchissement impossible : {}", t.toString());
        }
        try {
            if (XAERO_WORLDMAP) XaeroWorldMapSupport.refresh();
        } catch (Throwable t) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Xaero's World Map : rafraîchissement impossible : {}", t.toString());
        }
        try {
            if (XAERO_MINIMAP && !XAERO_WORLDMAP) XaeroMinimapSupport.refresh();
        } catch (Throwable t) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Xaero's Minimap : rafraîchissement impossible : {}", t.toString());
        }
    }
}
