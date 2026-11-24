package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** interact.button_lever couvre aussi les plaques de pression. */
@Mixin(BasePressurePlateBlock.class)
public abstract class BasePressurePlateBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void cpclaims$plate(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer p && !Protection.check(p, level, pos.getX(), pos.getZ(), Flags.BUTTON, false)) ci.cancel();
    }
}
