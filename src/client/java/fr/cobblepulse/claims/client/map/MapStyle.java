package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.client.ClientConfig;
import fr.cobblepulse.claims.client.ClientState;

import java.util.ArrayList;
import java.util.List;

/**
 * Couleurs par relation et calcul des pixels de territoires / bordure pour les cartes par blocs (Xaero). Les territoires
 * ne sont montres que sur les chunks decouverts (voir {@link Discovery}) ; la limite du monde reste toujours visible.
 */
public final class MapStyle {
    /** Bleu : clan ; orange : tiers ; violet : administration ; vert / cyan : vous. */
    public static final int BORDER_RGB = 0xFF3030;

    private MapStyle() {}

    public static boolean active(String dim) {
        return ClientConfig.get().map_overlays && dim != null && dim.equals(ClientState.dim);
    }

    public static List<ClientState.CClaim> overlapping(int x1, int z1, int x2, int z2) {
        List<ClientState.CClaim> out = new ArrayList<>();
        for (ClientState.CClaim c : ClientState.CLAIMS.values()) {
            if (c.minX <= x2 && c.maxX >= x1 && c.minZ <= z2 && c.maxZ >= z1) out.add(c);
        }
        out.sort((a, b) -> Boolean.compare(a.isSub(), b.isSub()));
        return out;
    }

    public static boolean borderCrosses(int x1, int z1, int x2, int z2) {
        ClientState.CBorder b = ClientState.border;
        if (b == null || !b.enabled || !ClientConfig.get().map_worldborder) return false;
        double i1 = b.inset(x1, z1), i2 = b.inset(x2 + 1, z1), i3 = b.inset(x1, z2 + 1), i4 = b.inset(x2 + 1, z2 + 1);
        double min = Math.min(Math.min(i1, i2), Math.min(i3, i4)), max = Math.max(Math.max(i1, i2), Math.max(i3, i4));
        if (b.circle) {
            // un cercle peut traverser un carre sans toucher ses coins : distance au centre
            double cx = Math.max(x1, Math.min(b.cx, x2 + 1)), cz = Math.max(z1, Math.min(b.cz, z2 + 1));
            max = Math.max(max, b.radius - Math.hypot(cx - b.cx, cz - b.cz));
        }
        return min <= 1 && max >= -1;
    }

    /** Region Xaero (32 x 32 chunks) : y a-t-il quelque chose a peindre ? */
    public static boolean regionHasHighlights(int regionX, int regionZ) {
        int x1 = regionX << 9, z1 = regionZ << 9;
        if (borderCrosses(x1, z1, x1 + 511, z1 + 511)) return true;
        for (ClientState.CClaim c : overlapping(x1, z1, x1 + 511, z1 + 511)) {
            if (Discovery.anyVisible(Math.max(c.minX, x1), Math.max(c.minZ, z1), Math.min(c.maxX, x1 + 511), Math.min(c.maxZ, z1 + 511))) return true;
        }
        return false;
    }

    /** Chunk a peindre : territoire sur un chunk decouvert, ou trace de la limite du monde (toujours visible). */
    public static boolean chunkHighlit(int chunkX, int chunkZ) {
        int x1 = chunkX << 4, z1 = chunkZ << 4;
        return (Discovery.visible(chunkX, chunkZ) && !overlapping(x1, z1, x1 + 15, z1 + 15).isEmpty()) || borderCrosses(x1, z1, x1 + 15, z1 + 15);
    }

    public static int regionHash(int x1, int z1, int x2, int z2) {
        int h = ClientConfig.get().map_overlays ? 1 : 0;
        h = h * 31 + (ClientConfig.get().map_worldborder ? 1 : 0);
        List<ClientState.CClaim> over = overlapping(x1, z1, x2, z2);
        if (!over.isEmpty()) h = h * 31 + Discovery.regionHash(x1 >> 9, z1 >> 9);
        for (ClientState.CClaim c : over) {
            h = h * 31 + c.id;
            h = h * 31 + c.minX * 7 + c.minZ * 13 + c.maxX * 17 + c.maxZ * 19;
            h = h * 31 + (c.rel == null ? 0 : c.rel.hashCode());
            h = h * 31 + (c.name == null ? 0 : c.name.hashCode());
        }
        if (borderCrosses(x1, z1, x2, z2)) {
            ClientState.CBorder b = ClientState.border;
            h = h * 31 + Double.hashCode(b.cx + b.cz * 3 + b.radius * 7 + b.sx * 11 + b.sz * 13) + (b.circle ? 1 : 2);
        }
        return h;
    }

    public interface Packer {
        int pack(int rgb, int alpha);
    }

    /** Remplit out[z<<4|x] pour le chunk ; renvoie false si rien n'est a dessiner. */
    public static boolean chunkPixels(int chunkX, int chunkZ, int[] out, Packer packer) {
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        java.util.Arrays.fill(out, 0, 256, 0);
        boolean any = false;
        // decouverte : les territoires ne sont peints que sur les chunks deja explores par le joueur
        List<ClientState.CClaim> claims = Discovery.visible(chunkX, chunkZ) ? overlapping(x0, z0, x0 + 15, z0 + 15) : List.of();
        for (ClientState.CClaim c : claims) {
            int rgb = c.isSub() ? 0xFFFFFF : c.rgb();
            int fill = packer.pack(rgb, c.isSub() ? 0x22 : 0x48);
            int edge = packer.pack(rgb, 0xD0);
            for (int x = Math.max(c.minX, x0); x <= Math.min(c.maxX, x0 + 15); x++) {
                for (int z = Math.max(c.minZ, z0); z <= Math.min(c.maxZ, z0 + 15); z++) {
                    boolean onEdge = x == c.minX || x == c.maxX || z == c.minZ || z == c.maxZ;
                    if (c.isSub() && !onEdge) continue;
                    out[(z - z0) << 4 | (x - x0)] = onEdge ? edge : fill;
                    any = true;
                }
            }
        }
        if (borderCrosses(x0, z0, x0 + 15, z0 + 15)) {
            ClientState.CBorder b = ClientState.border;
            int line = packer.pack(BORDER_RGB, 0xE0);
            int outside = packer.pack(BORDER_RGB, 0x30);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    double in = b.inset(x0 + x + 0.5, z0 + z + 0.5);
                    if (Math.abs(in) < 0.8) {
                        out[z << 4 | x] = line;
                        any = true;
                    } else if (in < 0 && in > -4) {
                        out[z << 4 | x] = outside;
                        any = true;
                    }
                }
            }
        }
        return any;
    }

    public static ClientState.CClaim claimAt(String dim, int x, int z) {
        if (!active(dim) || !Discovery.visible(x >> 4, z >> 4)) return null;
        return ClientState.at(x, z);
    }

    public static String shortTip(ClientState.CClaim c) {
        return c.name + (c.isAdmin() ? " · Zone Administrative" : " · " + c.owner + (c.clan == null ? "" : " [" + c.clan + "]"));
    }
}
