package fr.cobblepulse.claims.client.map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/** Enregistrement (mixin HighlighterRegistry#end) et rafraichissement du surligneur de Xaero's World Map. */
public final class XaeroWorldMapSupport {
    public static volatile int registered;

    private XaeroWorldMapSupport() {}

    public static void register(HighlighterRegistry registry) {
        registry.register(new XaeroWorldMapHighlighter());
        registered++;
    }

    public static void refresh() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || session.getMapProcessor() == null) return;
        MapWorld world = session.getMapProcessor().getMapWorld();
        if (world == null) return;
        String dim = fr.cobblepulse.claims.client.ClientState.dim;
        MapDimension md = null;
        if (dim != null && !dim.isEmpty()) md = world.getDimension(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
        if (md == null) md = world.getCurrentDimension();
        if (md != null && md.getHighlightHandler() != null) md.getHighlightHandler().clearCachedHashes();
    }
}
