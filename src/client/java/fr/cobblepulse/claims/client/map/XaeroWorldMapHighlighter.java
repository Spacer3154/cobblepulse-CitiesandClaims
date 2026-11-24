package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.client.ClientState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.AbstractHighlighter;

import java.util.List;

/**
 * Xaero's World Map : territoires peints au bloc pres (couleur par relation, bord plus opaque), trace de la WorldBorder
 * et infobulles au survol (nom, proprietaire, tag de clan). Aussi relaye sur Xaero's Minimap quand les deux sont installes.
 */
public class XaeroWorldMapHighlighter extends AbstractHighlighter {
    /** Couleurs Xaero : B << 24 | G << 16 | R << 8 | alpha. */
    static final MapStyle.Packer PACK = (rgb, a) -> (rgb & 0xFF) << 24 | (rgb >> 8 & 0xFF) << 16 | (rgb >> 16 & 0xFF) << 8 | (a & 0xFF);

    public XaeroWorldMapHighlighter() {
        super(false);
    }

    private static String d(ResourceKey<Level> dim) {
        return dim == null ? null : dim.location().toString();
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dim, int regionX, int regionZ) {
        if (!MapStyle.active(d(dim))) return 0;
        return MapStyle.regionHash(regionX << 9, regionZ << 9, (regionX << 9) + 511, (regionZ << 9) + 511);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
        if (!MapStyle.active(d(dim))) return false;
        return MapStyle.regionHasHighlights(regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        if (!MapStyle.active(d(dim))) return false;
        return MapStyle.chunkHighlit(chunkX, chunkZ);
    }

    @Override
    public int[] getChunkHighlitColor(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        if (!MapStyle.active(d(dim))) return null;
        return MapStyle.chunkPixels(chunkX, chunkZ, resultStore, PACK) ? resultStore : null;
    }

    @Override
    public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dim, int x, int z) {
        ClientState.CClaim c = MapStyle.claimAt(d(dim), x, z);
        return c == null ? null : Component.literal(c.name);
    }

    @Override
    public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dim, int x, int z) {
        ClientState.CClaim c = MapStyle.claimAt(d(dim), x, z);
        return c == null ? null : Component.literal(MapStyle.shortTip(c));
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int z, int width) {
        ClientState.CClaim c = MapStyle.claimAt(d(dim), x, z);
        if (c != null) list.add(Component.literal(MapStyle.shortTip(c)));
    }
}
