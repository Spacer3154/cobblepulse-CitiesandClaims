package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Boules de feu (Blaze, distributeur, joueur) : env.projectile_block_grief, ou env.fire_ignite si un joueur l'a lancee. */
@Mixin(SmallFireball.class)
public abstract class GriefSmallFireballMixin {
    @WrapOperation(method = "onHitBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean cpclaims$fire(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        SmallFireball self = (SmallFireball) (Object) this;
        if (self.getOwner() instanceof ServerPlayer p) {
            if (!Protection.check(p, level, pos.getX(), pos.getZ(), Flags.FIRE_IGNITE, true)) return false;
        } else if (!Protection.allowsZone(level, pos, Flags.PROJECTILE_GRIEF)) return false;
        return original.call(level, pos, state);
    }
}
