package fr.cobblepulse.claims.claim;

import com.mojang.authlib.GameProfile;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.economy.BlockEconomy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Pseudo -> UUID : joueurs en ligne, joueurs connus du mod, cache de profils du serveur. */
public final class Names {
    public record Resolved(UUID uuid, String name) {}

    private Names() {}

    public static Resolved resolve(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new Resolved(online.getUUID(), online.getGameProfile().getName());
        PlayerClaimData d = BlockEconomy.byName(name);
        if (d != null) return new Resolved(d.uuid, d.name);
        try {
            if (server.getProfileCache() != null) {
                Optional<GameProfile> gp = server.getProfileCache().get(name);
                if (gp.isPresent()) return new Resolved(gp.get().getId(), gp.get().getName());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
