package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** fluid.flow_in : un liquide exterieur ne coule pas dans un claim qui l'interdit. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void cpclaims$flow(BlockGetter getter, BlockPos from, BlockState fromState, Direction dir, BlockPos to, BlockState toState,
                               FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!(getter instanceof ServerLevel level)) return;
        if ((from.getX() >> 4) == (to.getX() >> 4) && (from.getZ() >> 4) == (to.getZ() >> 4) && from.getX() == to.getX() && from.getZ() == to.getZ()) return;
        String d = ClaimManager.dim(level);
        Claim target = ClaimManager.topAt(d, to.getX(), to.getZ());
        if (target == null) return;
        Claim source = ClaimManager.topAt(d, from.getX(), from.getZ());
        if (source == target) return;
        if (!Access.zone(target, Flags.FLOW_IN)) cir.setReturnValue(false);
    }
}
