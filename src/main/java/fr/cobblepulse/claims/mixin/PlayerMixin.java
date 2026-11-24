package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.zone.ZoneEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** admin.keep_inventory (pas de drop a la mort) et admin.hunger_drain (pas de perte de faim). */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void cpclaims$keep(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (ZoneEffects.keepInventory(self)) {
            ZoneEffects.KEEP.add(self.getUUID());
            ci.cancel();
        }
    }

    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void cpclaims$hunger(float amount, CallbackInfo ci) {
        if (ZoneEffects.hungerBlocked((Player) (Object) this)) ci.cancel();
    }
}
