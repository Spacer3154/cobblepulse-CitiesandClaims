package fr.cobblepulse.claims.client.map;

import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;

/** Enregistrement (mixin HighlighterRegistry#end) et rafraichissement du surligneur de Xaero's Minimap. */
public final class XaeroMinimapSupport {
    public static volatile int registered;

    private XaeroMinimapSupport() {}

    public static void register(HighlighterRegistry registry) {
        registry.register(new XaeroMinimapHighlighter());
        registered++;
    }

    public static void refresh() {
        MinimapSession s = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (s == null || s.getProcessor() == null || s.getProcessor().getMinimapWriter() == null) return;
        var h = s.getProcessor().getMinimapWriter().getDimensionHighlightHandler();
        if (h != null) h.requestRefresh();
    }
}
