package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions : les blocs proteges sont retires de la liste avant leur destruction (et avant l'envoi aux clients).
 * env.explosion_block ; + env.mob_grief_blocks (Creepers...), env.wither_dragon_grief, env.projectile_block_grief.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Entity source;
    @Shadow @Final private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void cpclaims$filter(boolean spawnParticles, CallbackInfo ci) {
        if (level.isClientSide() || toBlow.isEmpty()) return;
        String d = ClaimManager.dim(level);
        Entity owner = source instanceof Projectile pr ? pr.getOwner() : null;
        boolean mob = source instanceof Mob && !(source instanceof WitherBoss) && !(source instanceof EnderDragon);
        boolean witherDragon = source instanceof WitherBoss || source instanceof EnderDragon || owner instanceof WitherBoss;
        boolean mobProjectile = source instanceof Projectile && !(owner instanceof Player);
        toBlow.removeIf(pos -> {
            Claim c = ClaimManager.topAt(d, pos.getX(), pos.getZ());
            if (c == null) return false;
            Claim inner = ClaimManager.at(d, pos.getX(), pos.getZ());
            if (!Access.zone(inner, Flags.EXPLOSION_BLOCK)) return true;
            if (mob && !Access.zone(inner, Flags.MOB_GRIEF)) return true;
            if (witherDragon && !Access.zone(inner, Flags.WITHER_DRAGON)) return true;
            return mobProjectile && !Access.zone(inner, Flags.PROJECTILE_GRIEF);
        });
    }
}
