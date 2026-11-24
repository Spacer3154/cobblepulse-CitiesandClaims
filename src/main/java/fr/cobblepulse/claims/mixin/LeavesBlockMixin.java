package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** env.leaf_decay. */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void cpclaims$decay(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Protection.allowsZone(level, pos, Flags.LEAF_DECAY)) ci.cancel();
    }
}
