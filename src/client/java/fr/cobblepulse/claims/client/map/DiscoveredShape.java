package fr.cobblepulse.claims.client.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Partie decouverte d'un territoire, pour les cartes vectorielles (JourneyMap) : le rectangle est decoupe sur la grille
 * des chunks, les cellules decouvertes sont fusionnees et leur contour suivi. Resultat : un ou plusieurs polygones,
 * chacun avec ses trous eventuels, en coordonnees de bords de blocs (maxX + 1).
 */
public final class DiscoveredShape {
    public interface ChunkTest {
        boolean visible(int cx, int cz);
    }

    /** Contour ferme : sommets (x, z) dans l'ordre. */
    public record Loop(int[] xs, int[] zs) {
        public double signedArea() {
            double a = 0;
            for (int k = 0; k < xs.length; k++) {
                int n = (k + 1) % xs.length;
                a += (double) xs[k] * zs[n] - (double) xs[n] * zs[k];
            }
            return a / 2;
        }

        boolean contains(double px, double pz) {
            boolean in = false;
            for (int k = 0, j = xs.length - 1; k < xs.length; j = k++) {
                if ((zs[k] > pz) != (zs[j] > pz) && px < (double) (xs[j] - xs[k]) * (pz - zs[k]) / (zs[j] - zs[k]) + xs[k]) in = !in;
            }
            return in;
        }
    }

    public record Piece(Loop outer, List<Loop> holes) {
        public double area() {
            double a = Math.abs(outer.signedArea());
            for (Loop h : holes) a -= Math.abs(h.signedArea());
            return a;
        }
    }

    /** Etat d'un territoire sur la carte. */
    public enum Coverage { NONE, PARTIAL, FULL }

    private DiscoveredShape() {}

    static int[] grid(int a, int b) {
        List<Integer> l = new ArrayList<>();
        l.add(a);
        int m = Math.floorDiv(a, 16) * 16;
        if (m <= a) m += 16;
        for (; m < b; m += 16) l.add(m);
        l.add(b);
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    public static Coverage coverage(int minX, int minZ, int maxX, int maxZ, ChunkTest test) {
        boolean any = false, all = true;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (test.visible(cx, cz)) any = true;
                else all = false;
                if (any && !all) return Coverage.PARTIAL;
            }
        }
        return all ? Coverage.FULL : Coverage.NONE;
    }

    /** Morceaux visibles du rectangle de blocs [minX..maxX] x [minZ..maxZ]. */
    public static List<Piece> visible(int minX, int minZ, int maxX, int maxZ, ChunkTest test) {
        int[] gx = grid(minX, maxX + 1), gz = grid(minZ, maxZ + 1);
        int nx = gx.length - 1, nz = gz.length - 1;
        boolean[] in = new boolean[nx * nz];
        int count = 0;
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                if (test.visible(Math.floorDiv(gx[i], 16), Math.floorDiv(gz[j], 16))) {
                    in[j * nx + i] = true;
                    count++;
                }
            }
        }
        List<Piece> out = new ArrayList<>();
        if (count == 0) return out;
        if (count == in.length) {
            out.add(new Piece(new Loop(new int[]{minX, maxX + 1, maxX + 1, minX}, new int[]{minZ, minZ, maxZ + 1, maxZ + 1}), List.of()));
            return out;
        }
        // aretes de bord orientees (interieur a droite, x vers l'est, z vers le sud) : 0 +x, 1 +z, 2 -x, 3 -z
        List<int[]> edges = new ArrayList<>(); // {fromI, fromJ, dir}
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                if (!in[j * nx + i]) continue;
                if (j == 0 || !in[(j - 1) * nx + i]) edges.add(new int[]{i, j, 0});
                if (i == nx - 1 || !in[j * nx + i + 1]) edges.add(new int[]{i + 1, j, 1});
                if (j == nz - 1 || !in[(j + 1) * nx + i]) edges.add(new int[]{i + 1, j + 1, 2});
                if (i == 0 || !in[j * nx + i - 1]) edges.add(new int[]{i, j + 1, 3});
            }
        }
        Map<Long, List<Integer>> from = new HashMap<>();
        for (int e = 0; e < edges.size(); e++) {
            from.computeIfAbsent(vkey(edges.get(e)[0], edges.get(e)[1]), k -> new ArrayList<>(2)).add(e);
        }
        boolean[] used = new boolean[edges.size()];
        List<Loop> outers = new ArrayList<>(), holes = new ArrayList<>();
        List<int[]> holeProbe = new ArrayList<>();
        for (int start = 0; start < edges.size(); start++) {
            if (used[start]) continue;
            List<Integer> loop = new ArrayList<>();
            int cur = start;
            while (!used[cur]) {
                used[cur] = true;
                loop.add(cur);
                int[] e = edges.get(cur);
                int ti = e[0] + DX[e[2]], tj = e[1] + DZ[e[2]];
                int next = -1, best = 9;
                for (int cand : from.getOrDefault(vkey(ti, tj), List.of())) {
                    int turn = Math.floorMod(edges.get(cand)[2] - e[2], 4);
                    int rank = turn == 1 ? 0 : turn == 0 ? 1 : 2; // a droite d'abord : les cellules en diagonale restent separees
                    if (turn != 2 && rank < best) {
                        best = rank;
                        next = cand;
                    }
                }
                if (next < 0) break;
                cur = next;
            }
            // sommets utiles (changements de direction)
            List<int[]> pts = new ArrayList<>();
            for (int k = 0; k < loop.size(); k++) {
                int[] e = edges.get(loop.get(k));
                int[] prev = edges.get(loop.get((k + loop.size() - 1) % loop.size()));
                if (prev[2] != e[2]) pts.add(new int[]{gx[e[0]], gz[e[1]]});
            }
            if (pts.size() < 3) continue;
            int[] xs = new int[pts.size()], zs = new int[pts.size()];
            for (int k = 0; k < xs.length; k++) {
                xs[k] = pts.get(k)[0];
                zs[k] = pts.get(k)[1];
            }
            Loop l = new Loop(xs, zs);
            if (l.signedArea() > 0) outers.add(l);
            else {
                holes.add(l);
                int[] e = edges.get(loop.get(0));
                holeProbe.add(new int[]{gx[e[0]], gz[e[1]], gx[e[0] + DX[e[2]]], gz[e[1] + DZ[e[2]]], e[2]});
            }
        }
        List<List<Loop>> holesOf = new ArrayList<>();
        for (int k = 0; k < outers.size(); k++) holesOf.add(new ArrayList<>());
        for (int h = 0; h < holes.size(); h++) {
            int[] p = holeProbe.get(h);
            // point juste a gauche de l'arete : dans le trou
            double px = (p[0] + p[2]) / 2.0 + LX[p[4]] * 0.25, pz = (p[1] + p[3]) / 2.0 + LZ[p[4]] * 0.25;
            int bestOuter = -1;
            double bestArea = Double.MAX_VALUE;
            for (int k = 0; k < outers.size(); k++) {
                double a = outers.get(k).signedArea();
                if (a < bestArea && outers.get(k).contains(px, pz)) {
                    bestArea = a;
                    bestOuter = k;
                }
            }
            if (bestOuter >= 0) holesOf.get(bestOuter).add(holes.get(h));
        }
        for (int k = 0; k < outers.size(); k++) out.add(new Piece(outers.get(k), holesOf.get(k)));
        return out;
    }

    private static final int[] DX = {1, 0, -1, 0}, DZ = {0, 1, 0, -1};
    /** Normale a gauche de chaque direction. */
    private static final int[] LX = {0, 1, 0, -1}, LZ = {-1, 0, 1, 0};

    private static long vkey(int i, int j) {
        return (long) i << 32 | j & 0xFFFFFFFFL;
    }

    /** Aire decouverte attendue (verification) : somme des cellules visibles. */
    public static long bruteArea(int minX, int minZ, int maxX, int maxZ, ChunkTest test) {
        long a = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) if (test.visible(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) a++;
        }
        return a;
    }
}
