package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.client.ClientState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/** Xaero's Minimap seule (sans World Map) : perimetres des territoires et limite du monde en temps reel. */
public class XaeroMinimapHighlighter extends AbstractHighlighter {
    public XaeroMinimapHighlighter() {
        super(false);
    }

    private static String d(ResourceKey<Level> dim) {
        return dim == null ? null : dim.location().toString();
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
        if (!MapStyle.active(d(dim))) return false;
        int x1 = regionX << 9, z1 = regionZ << 9;
        return !MapStyle.overlapping(x1, z1, x1 + 511, z1 + 511).isEmpty() || MapStyle.borderCrosses(x1, z1, x1 + 511, z1 + 511);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        if (!MapStyle.active(d(dim))) return false;
        int x1 = chunkX << 4, z1 = chunkZ << 4;
        return !MapStyle.overlapping(x1, z1, x1 + 15, z1 + 15).isEmpty() || MapStyle.borderCrosses(x1, z1, x1 + 15, z1 + 15);
    }

    @Override
    public int[] getChunkHighlitColor(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        if (!MapStyle.active(d(dim))) return null;
        return MapStyle.chunkPixels(chunkX, chunkZ, resultStore, XaeroWorldMapHighlighter.PACK) ? resultStore : null;
    }

    @Override
    public void addBlockHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dim, int x, int z, int width) {
        ClientState.CClaim c = MapStyle.claimAt(d(dim), x, z);
        if (c != null) compiler.addLine(MapStyle.shortTip(c));
    }
}
