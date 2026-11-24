package fr.cobblepulse.claims.protect;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.zone.ZoneEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SaddleItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Evenements Fabric : casse, clics droits (blocs, objets, entites), attaques et degats. */
public final class ProtectionEvents {
    private ProtectionEvents() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p)) return true;
            BlockEconomy.markActive(p);
            return Protection.check(p, level, pos, Interactions.forBreak(state));
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
            BlockEconomy.markActive(p);
            return useBlock(p, level, hand, hit) ? InteractionResult.PASS : InteractionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide() || !(player instanceof ServerPlayer p)) return InteractionResultHolder.pass(stack);
            BlockEconomy.markActive(p);
            if (!useItem(p, level, stack)) {
                p.containerMenu.sendAllDataToRemote();
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
            BlockEconomy.markActive(p);
            Flag f = forEntityUse(p, entity, p.getItemInHand(hand));
            if (f == null || Protection.check(p, level, entity.blockPosition(), f)) return InteractionResult.PASS;
            p.containerMenu.sendAllDataToRemote();
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
            BlockEconomy.markActive(p);
            Flag f = forAttack(p, entity, false);
            if (f == null || Protection.check(p, level, entity.blockPosition(), f)) return InteractionResult.PASS;
            return InteractionResult.FAIL;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ProtectionEvents::allowDamage);
    }

    // ------------------------------------------------------------------ blocs

    static boolean useBlock(ServerPlayer p, Level level, InteractionHand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = p.getItemInHand(hand);
        boolean handsEmpty = p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty();
        boolean skipsBlock = p.isSecondaryUseActive() && !handsEmpty;
        Flag blockFlag = skipsBlock ? null : Interactions.forBlock(state);
        if (blockFlag != null) {
            if (!Protection.check(p, level, pos, blockFlag)) {
                resync(p, level, pos);
                return false;
            }
            return true;
        }
        Interactions.ItemUse use = held.isEmpty() ? null : Interactions.forItemOnBlock(held.getItem());
        if (use != null) {
            BlockPos at = use.relative() && !state.canBeReplaced() ? pos.relative(hit.getDirection()) : pos;
            if (!Protection.check(p, level, at, use.flag()) || (use.flag() == Flags.FIRE_IGNITE && !Protection.check(p, level, pos, use.flag()))) {
                resync(p, level, pos);
                p.containerMenu.sendAllDataToRemote();
                return false;
            }
        }
        return true;
    }

    static void resync(ServerPlayer p, Level level, BlockPos pos) {
        for (BlockPos q : new BlockPos[]{pos, pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            p.connection.send(new ClientboundBlockUpdatePacket(level, q));
        }
    }

    /** Seaux et bateaux (cibles par lancer de rayon), objets utilises dans l'air. */
    static boolean useItem(ServerPlayer p, Level level, ItemStack stack) {
        if (stack.getItem() instanceof BucketItem) {
            boolean empty = stack.is(Items.BUCKET);
            HitResult hr = p.pick(p.blockInteractionRange(), 0f, empty);
            if (!(hr instanceof BlockHitResult bh) || hr.getType() != HitResult.Type.BLOCK) return true;
            BlockPos pos = bh.getBlockPos();
            BlockPos rel = pos.relative(bh.getDirection());
            Flag f;
            if (empty) {
                FluidState fs = level.getFluidState(pos);
                BlockState bs = level.getBlockState(pos);
                if (bs.is(Blocks.POWDER_SNOW)) f = Flags.POWDER_SNOW;
                else if (fs.is(Fluids.LAVA) || fs.is(Fluids.FLOWING_LAVA)) f = Flags.LAVA;
                else f = Flags.WATER;
                return Protection.check(p, level, pos, f);
            }
            f = stack.is(Items.LAVA_BUCKET) ? Flags.LAVA : Flags.WATER;
            return Protection.check(p, level, pos, f) && Protection.check(p, level, rel, f);
        }
        if (stack.getItem() instanceof BoatItem) {
            HitResult hr = p.pick(p.blockInteractionRange(), 0f, true);
            if (hr.getType() == HitResult.Type.BLOCK) return Protection.check(p, level, BlockPos.containing(hr.getLocation()), Flags.VEHICLE_PLACE);
        }
        return true;
    }

    // ------------------------------------------------------------------ entites

    static Flag forEntityUse(ServerPlayer p, Entity e, ItemStack held) {
        if (e instanceof ServerPlayer) return null;
        if (held.getItem() instanceof LeadItem || held.getItem() instanceof NameTagItem) {
            if (e instanceof Mob) return Flags.LEASH;
        }
        if (e instanceof ItemFrame || e instanceof ArmorStand) return Flags.FRAME;
        if (e instanceof NPCEntity) return Flags.NPC;
        if (e instanceof PokemonEntity pe) {
            // Pokemon d'un autre joueur : objets donnes, montures... gere par Cobblemon ; laisse/etiquette ci-dessus
            return null;
        }
        if (e instanceof AbstractVillager) return Flags.TRADE;
        if (e instanceof ContainerEntity) return Flags.CHEST;
        if (e instanceof Boat || e instanceof AbstractMinecart) return Flags.VEHICLE_RIDE;
        if (e instanceof Animal a) {
            if (!held.isEmpty() && (a.isFood(held) || held.is(Items.SHEARS) || held.is(Items.BUCKET) || held.is(Items.BOWL)
                    || held.getItem() instanceof SaddleItem)) return Flags.BREED;
            if (e instanceof AbstractHorse || (e instanceof Saddleable s && s.isSaddled())) return Flags.VEHICLE_RIDE;
        }
        return null;
    }

    /** Flag d'une attaque directe ou par projectile ; null si non protegee (ou proprietaire de son propre Pokemon). */
    static Flag forAttack(ServerPlayer attacker, Entity e, boolean projectile) {
        if (e instanceof Player) return e == attacker ? null : Flags.PVP;
        if (e instanceof PokemonEntity pe) {
            java.util.UUID owner = pe.getPokemon().getOwnerUUID();
            if (owner != null && owner.equals(attacker.getUUID())) return null;
            boolean owned = owner != null || pe.getTethering() != null || !pe.getPokemon().isWild();
            return owned ? Flags.HURT_OWNED : Flags.HURT_WILD;
        }
        if (e instanceof ItemFrame || e instanceof ArmorStand) return Flags.FRAME;
        if (e instanceof HangingEntity) return Flags.BLOCK_BREAK;
        if (e instanceof Boat || e instanceof AbstractMinecart) return Flags.VEHICLE_PLACE;
        if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) return Flags.BLOCK_BREAK;
        if (e instanceof Enemy) return Flags.HURT_HOSTILE;
        if (e instanceof LivingEntity) return Flags.HURT_PASSIVE;
        return null;
    }

    static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        Level level = target.level();
        if (level.isClientSide()) return true;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return true;
        if (target instanceof ServerPlayer sp) {
            if (ZoneEffects.invulnerable(sp)) return false;
            if (source.is(DamageTypeTags.IS_FALL) && ZoneEffects.inFallGrace(sp)) return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            if (!Protection.allowsZone(level, target.blockPosition(), Flags.EXPLOSION_ENTITY)) return false;
        }
        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker && attacker != target) {
            boolean projectile = source.getDirectEntity() instanceof Projectile;
            Flag f = forAttack(attacker, target, projectile);
            if (f != null && !Protection.check(attacker, level, target.blockPosition(), f)) return false;
            if (projectile && target instanceof PokemonEntity pe) {
                java.util.UUID owner = pe.getPokemon().getOwnerUUID();
                if ((owner == null || !owner.equals(attacker.getUUID())) && !Protection.check(attacker, level, target.blockPosition(), Flags.KNOCKBACK))
                    return false;
            }
            // PvP : la zone de l'attaquant compte aussi (pas de tir depuis un claim protege)
            if (target instanceof ServerPlayer && f == Flags.PVP) {
                Claim from = ClaimManager.at(level, attacker.blockPosition());
                if (from != null && !Access.allows(attacker, from, Flags.PVP)) return false;
            }
        }
        return true;
    }
}
