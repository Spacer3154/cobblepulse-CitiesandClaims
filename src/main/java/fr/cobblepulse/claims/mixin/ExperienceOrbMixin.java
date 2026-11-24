package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** item.xp_pickup. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void cpclaims$xp(Player player, CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (!Protection.check(player, self.level(), self.getBlockX(), self.getBlockZ(), Flags.XP, false)) ci.cancel();
    }
}
