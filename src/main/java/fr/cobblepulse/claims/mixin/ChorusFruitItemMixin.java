package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ChorusFruitItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** move.chorus_fruit : les destinations interdites (claim, bordure) sont ignorees. */
@Mixin(ChorusFruitItem.class)
public abstract class ChorusFruitItemMixin {
    @WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private boolean cpclaims$chorus(LivingEntity entity, double x, double y, double z, boolean particles, Operation<Boolean> original) {
        if (entity instanceof ServerPlayer p) {
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!WorldBorders.inside(ClaimManager.dim(p.level()), x, z)) return false;
            if (!Protection.check(p, p.level(), bx, bz, Flags.CHORUS, false) || !Protection.check(p, p.level(), bx, bz, Flags.ENTER, false)) return false;
        }
        return original.call(entity, x, y, z, particles);
    }
}
