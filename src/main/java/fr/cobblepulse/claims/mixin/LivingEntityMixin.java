package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.zone.ZoneEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** admin.keep_inventory : l'XP n'est pas lachee non plus (restauree a la reapparition). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void cpclaims$keepXp(Entity killer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player p && ZoneEffects.KEEP.contains(p.getUUID())) ci.cancel();
    }
}
