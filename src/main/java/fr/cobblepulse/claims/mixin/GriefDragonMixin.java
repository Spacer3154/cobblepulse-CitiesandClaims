package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.wither_dragon_grief : l'Ender Dragon ne detruit pas les blocs d'un claim protege. */
@Mixin(EnderDragon.class)
public abstract class GriefDragonMixin {
    @WrapOperation(method = "checkWalls", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean cpclaims$dragon(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.WITHER_DRAGON)) return false;
        return original.call(level, pos, moving);
    }
}
