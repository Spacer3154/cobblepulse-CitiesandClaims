package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** piston.cross_border : un piston ne pousse / tire / casse rien dans un autre claim qui l'interdit. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pistonPos;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private BlockPos startPos;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void cpclaims$resolve(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || level.isClientSide()) return;
        String d = ClaimManager.dim(level);
        Claim home = ClaimManager.topAt(d, pistonPos.getX(), pistonPos.getZ());
        if (blocked(d, home, startPos)) {
            cir.setReturnValue(false);
            return;
        }
        for (BlockPos p : toPush) {
            if (blocked(d, home, p) || blocked(d, home, p.relative(pushDirection))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos p : toDestroy) {
            if (blocked(d, home, p)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    private static boolean blocked(String d, Claim home, BlockPos p) {
        Claim c = ClaimManager.topAt(d, p.getX(), p.getZ());
        return c != null && c != home && !Access.zone(c, Flags.PISTON);
    }
}
