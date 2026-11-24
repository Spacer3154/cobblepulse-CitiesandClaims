package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.fire_spread : le feu ne se propage pas et ne consume aucun bloc d'un claim protege. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    /** Propagation vers un nouveau bloc (drapeau 3) ; la mise a jour de l'age du feu lui-meme utilise le drapeau 4. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cpclaims$spread(ServerLevel level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        if (flags == 3 && !Protection.allowsZone(level, pos, Flags.FIRE_SPREAD)) return false;
        return original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cpclaims$burnSet(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.FIRE_SPREAD)) return false;
        return original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean cpclaims$burnRemove(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.FIRE_SPREAD)) return false;
        return original.call(level, pos, moving);
    }
}
