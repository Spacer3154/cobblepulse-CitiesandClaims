package fr.cobblepulse.claims.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** cobblemon.pasture_manage : deposer un Pokemon dans un Paturage d'un claim. */
@Mixin(value = PokemonPastureBlockEntity.class, remap = false)
public abstract class PastureBlockEntityMixin {
    @Inject(method = "tether", at = @At("HEAD"), cancellable = true, remap = false)
    private void cpclaims$tether(ServerPlayer player, Pokemon pokemon, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity) (Object) this;
        if (self.getLevel() != null && !Protection.check(player, self.getLevel(), self.getBlockPos(), Flags.PASTURE_MANAGE)) cir.setReturnValue(false);
    }
}
