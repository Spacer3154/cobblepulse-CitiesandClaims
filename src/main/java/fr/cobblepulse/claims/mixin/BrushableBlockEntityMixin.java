package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** block.brush_archaeology : pinceau sur sable / gravier suspect. */
@Mixin(BrushableBlockEntity.class)
public abstract class BrushableBlockEntityMixin {
    @Inject(method = "brush", at = @At("HEAD"), cancellable = true)
    private void cpclaims$brush(long time, Player player, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        BrushableBlockEntity self = (BrushableBlockEntity) (Object) this;
        if (self.getLevel() != null && !Protection.check(player, self.getLevel(), self.getBlockPos(), Flags.BRUSH)) cir.setReturnValue(false);
    }
}
