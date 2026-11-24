package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.mob_door_break : zombies et vindicateurs ne cassent pas les portes d'un claim protege. */
@Mixin(BreakDoorGoal.class)
public abstract class GriefBreakDoorMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean cpclaims$door(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_DOOR)) return false;
        return original.call(level, pos, moving);
    }
}
