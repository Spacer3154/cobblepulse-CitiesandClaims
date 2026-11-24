package fr.cobblepulse.claims.mixin;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import fr.cobblepulse.claims.integration.CobblemonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** admin.level_cap : niveau impose aux Pokemon des combats lances dans une arene. */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMixin {
    @ModifyVariable(method = "startBattle", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static BattleFormat cpclaims$levelCap(BattleFormat format, BattleFormat same, BattleSide side1, BattleSide side2) {
        try {
            return CobblemonHooks.levelCap(format, side1, side2);
        } catch (Throwable t) {
            return format;
        }
    }
}
