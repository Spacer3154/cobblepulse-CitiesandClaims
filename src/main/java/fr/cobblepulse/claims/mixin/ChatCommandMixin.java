package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.protect.CommandGuard;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * command.execute et listes de commandes : interception des commandes tapees par le joueur, avant leur execution
 * (les commandes lancees par le serveur ou d'autres mods au nom du joueur ne sont pas filtrees).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatCommandMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "performUnsignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void cpclaims$unsigned(String command, CallbackInfo ci) {
        BlockEconomy.markActive(player);
        if (!CommandGuard.allow(player, command)) ci.cancel();
    }

    @Inject(method = "performSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void cpclaims$signed(ServerboundChatCommandSignedPacket packet, LastSeenMessages seen, CallbackInfo ci) {
        BlockEconomy.markActive(player);
        if (!CommandGuard.allow(player, packet.command())) ci.cancel();
    }
}
