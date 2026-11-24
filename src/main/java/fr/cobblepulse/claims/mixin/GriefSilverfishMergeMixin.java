package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.mob_grief_blocks : un Poisson d'argent n'infeste pas la pierre d'un claim protege. */
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal")
public abstract class GriefSilverfishMergeMixin {
    @WrapOperation(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cpclaims$merge(LevelAccessor level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        if (level instanceof Level l && !Protection.allowsZone(l, pos, Flags.MOB_GRIEF)) return false;
        return original.call(level, pos, state, flags);
    }
}
