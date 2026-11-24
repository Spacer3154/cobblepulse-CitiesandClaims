package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** admin.hostile_spawn / admin.passive_spawn : apparitions naturelles vanilla. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "isValidPositionForMob", at = @At("HEAD"), cancellable = true)
    private static void cpclaims$spawn(ServerLevel level, Mob mob, double dist, CallbackInfoReturnable<Boolean> cir) {
        boolean hostile = mob.getType().getCategory() == MobCategory.MONSTER;
        if (!Protection.allowsZone(level, mob.blockPosition(), hostile ? Flags.HOSTILE_SPAWN : Flags.PASSIVE_SPAWN)) cir.setReturnValue(false);
    }
}
