package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.mob_grief_blocks : un Ravageur ne casse pas les feuillages / cultures d'un claim protege. */
@Mixin(Ravager.class)
public abstract class GriefRavagerMixin {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean cpclaims$ravager(Level level, BlockPos pos, boolean drop, Entity e, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_GRIEF)) return false;
        return original.call(level, pos, drop, e);
    }
}
