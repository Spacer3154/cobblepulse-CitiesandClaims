package fr.cobblepulse.claims.client.mixin;

import fr.cobblepulse.claims.client.map.XaeroMinimapSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

/** Xaero's Minimap sans World Map : ajoute le surligneur des territoires a la session de la minimap. */
@Pseudo
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class XaeroMinimapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void cpclaims$register(CallbackInfo ci) {
        XaeroMinimapSupport.register((HighlighterRegistry) (Object) this);
    }
}
