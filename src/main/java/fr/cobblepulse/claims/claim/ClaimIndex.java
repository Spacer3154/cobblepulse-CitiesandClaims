package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.data.Claim;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Index spatial en memoire : par dimension, Map<ChunkPos, List<Claim>> (cle ChunkPos.asLong). Une verification de
 * permission ne lit que la liste du chunk concerne (quelques claims au plus) : temps constant par chunk.
 * Seuls les claims principaux sont indexes ; les sous-zones sont cherchees dans leur claim principal.
 */
final class ClaimIndex {
    private final Map<String, Long2ObjectOpenHashMap<List<Claim>>> byDim = new HashMap<>();

    static long key(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    void add(Claim c) {
        Long2ObjectOpenHashMap<List<Claim>> m = byDim.computeIfAbsent(c.dimension, k -> new Long2ObjectOpenHashMap<>());
        for (int cx = c.minX >> 4; cx <= c.maxX >> 4; cx++) {
            for (int cz = c.minZ >> 4; cz <= c.maxZ >> 4; cz++) {
                m.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(2)).add(c);
            }
        }
    }

    void remove(Claim c) {
        Long2ObjectOpenHashMap<List<Claim>> m = byDim.get(c.dimension);
        if (m == null) return;
        for (int cx = c.minX >> 4; cx <= c.maxX >> 4; cx++) {
            for (int cz = c.minZ >> 4; cz <= c.maxZ >> 4; cz++) {
                long k = key(cx, cz);
                List<Claim> l = m.get(k);
                if (l == null) continue;
                l.remove(c);
                if (l.isEmpty()) m.remove(k);
            }
        }
    }

    void clear() {
        byDim.clear();
    }

    List<Claim> inChunk(String dim, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<List<Claim>> m = byDim.get(dim);
        if (m == null) return Collections.emptyList();
        List<Claim> l = m.get(key(chunkX, chunkZ));
        return l == null ? Collections.emptyList() : l;
    }

    Claim topAt(String dim, int x, int z) {
        for (Claim c : inChunk(dim, x >> 4, z >> 4)) if (c.contains(x, z)) return c;
        return null;
    }

    /** Claims principaux qui chevauchent le rectangle. */
    Set<Claim> overlapping(String dim, int x1, int z1, int x2, int z2) {
        Set<Claim> out = new LinkedHashSet<>();
        Long2ObjectOpenHashMap<List<Claim>> m = byDim.get(dim);
        if (m == null) return out;
        long chunks = (long) ((x2 >> 4) - (x1 >> 4) + 1) * ((z2 >> 4) - (z1 >> 4) + 1);
        if (chunks > m.size()) {
            for (List<Claim> l : m.values()) for (Claim c : l) if (c.overlaps(x1, z1, x2, z2)) out.add(c);
            return out;
        }
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                for (Claim c : inChunk(dim, cx, cz)) if (c.overlaps(x1, z1, x2, z2)) out.add(c);
            }
        }
        return out;
    }

    int chunkEntries() {
        int n = 0;
        for (Long2ObjectOpenHashMap<List<Claim>> m : byDim.values()) n += m.size();
        return n;
    }
}
