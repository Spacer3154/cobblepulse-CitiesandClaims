package fr.cobblepulse.claims.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** cobblemon.projectile_knockback (canne a peche) et PvP (tirer un joueur). */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
    @Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
    private void cpclaims$pull(Entity target, CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (!(self.getOwner() instanceof ServerPlayer p)) return;
        if (target instanceof PokemonEntity pe) {
            java.util.UUID owner = pe.getPokemon().getOwnerUUID();
            if (owner != null && owner.equals(p.getUUID())) return;
            if (!Protection.check(p, self.level(), target.blockPosition(), Flags.KNOCKBACK)) ci.cancel();
        } else if (target instanceof ServerPlayer && target != p) {
            if (!Protection.check(p, self.level(), target.blockPosition(), Flags.PVP)) ci.cancel();
        }
    }
}
