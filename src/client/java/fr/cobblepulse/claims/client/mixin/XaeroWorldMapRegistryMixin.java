package fr.cobblepulse.claims.client.mixin;

import fr.cobblepulse.claims.client.map.XaeroWorldMapSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

/** Ajoute le surligneur des territoires a chaque session de Xaero's World Map, avant le verrouillage du registre. */
@Pseudo
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class XaeroWorldMapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void cpclaims$register(CallbackInfo ci) {
        XaeroWorldMapSupport.register((HighlighterRegistry) (Object) this);
    }
}
