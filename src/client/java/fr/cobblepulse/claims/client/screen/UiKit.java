package fr.cobblepulse.claims.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Palette et aides de dessin (memes couleurs que les ecrans de CobblePulse Utils). */
public final class UiKit {
    public static final int PANEL = 0xF0141922;
    public static final int HEADER = 0xFF10151D;
    public static final int CARD = 0xFF1A202A;
    public static final int CARD_HOVER = 0xFF212A37;
    public static final int CARD_ON = 0xFF263347;
    public static final int BORDER = 0xFF2E3A4C;
    public static final int ACCENT = 0xFF5FD3F3;
    public static final int GOLD = 0xFFFFD24A;
    public static final int TEXT = 0xFFE8ECF2;
    public static final int MUTED = 0xFF8E9AAB;
    public static final int DIM = 0xFF5C6878;
    public static final int OK = 0xFF6BE07A;
    public static final int WARN = 0xFFFFA94D;
    public static final int ERROR = 0xFFFF6B6B;
    public static final int VIOLET = 0xFFB45CFF;

    private UiKit() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static boolean isHover(int mx, int my, int x, int y, int w, int h) {
        return in(mx, my, x, y, w, h);
    }

    public static String norm(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(java.util.Locale.ROOT);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int stateColor(String state) {
        return switch (state == null ? "" : state) {
            case "ALLOW" -> OK;
            case "DENY" -> ERROR;
            default -> MUTED;
        };
    }

    public static String stateLabel(String state) {
        return switch (state == null ? "" : state) {
            case "ALLOW" -> "✔ Autorisé";
            case "DENY" -> "✖ Interdit";
            default -> "◌ Défaut";
        };
    }

    public static String fmtDuration(long ms) {
        if (ms < 0) return "—";
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0) return h + " h " + (m % 60) + " min";
        if (m > 0) return m + " min " + String.format("%02d", s % 60) + " s";
        return s + " s";
    }

    public static String dimName(String dim) {
        if (dim == null) return "?";
        return switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dim;
        };
    }
}
