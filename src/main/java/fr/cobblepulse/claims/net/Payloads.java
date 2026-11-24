package fr.cobblepulse.claims.net;

import fr.cobblepulse.claims.CobblePulseClaims;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Paquets types (API CustomPayload de Minecraft 1.21.1). Le contenu detaille voyage en JSON. */
public final class Payloads {
    private static final StreamCodec<ByteBuf, String> BIG = ByteBufCodecs.stringUtf8(1 << 21);

    private Payloads() {}

    // ------------------------------------------------------------------ client -> serveur

    /** Touche contextuelle (Maj = interface d'administration). */
    public record ContextKeyPayload(boolean shift) implements CustomPacketPayload {
        public static final Type<ContextKeyPayload> TYPE = new Type<>(CobblePulseClaims.id("context_key"));
        public static final StreamCodec<ByteBuf, ContextKeyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ContextKeyPayload::shift, ContextKeyPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Modifications depuis les ecrans (permissions, commandes, nom, membres, parcelles, conversion, administration). */
    public record ClaimActionPayload(String action, String json) implements CustomPacketPayload {
        public static final Type<ClaimActionPayload> TYPE = new Type<>(CobblePulseClaims.id("claim_action"));
        public static final StreamCodec<ByteBuf, ClaimActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ClaimActionPayload::action, BIG, ClaimActionPayload::json, ClaimActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ serveur -> client

    /** Ouverture (ou rafraichissement) d'un ecran : manager, info, dashboard, admin. */
    public record OpenScreenPayload(String screen, String json) implements CustomPacketPayload {
        public static final Type<OpenScreenPayload> TYPE = new Type<>(CobblePulseClaims.id("open_screen"));
        public static final StreamCodec<ByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenScreenPayload::screen, BIG, OpenScreenPayload::json, OpenScreenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Bordures des territoires de la dimension du joueur (complet ou delta), avec la relation de chaque claim. */
    public record ClaimsSyncPayload(String json) implements CustomPacketPayload {
        public static final Type<ClaimsSyncPayload> TYPE = new Type<>(CobblePulseClaims.id("claims_sync"));
        public static final StreamCodec<ByteBuf, ClaimsSyncPayload> CODEC = StreamCodec.composite(BIG, ClaimsSyncPayload::json, ClaimsSyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** WorldBorder de la dimension du joueur. */
    public record BorderSyncPayload(String json) implements CustomPacketPayload {
        public static final Type<BorderSyncPayload> TYPE = new Type<>(CobblePulseClaims.id("border_sync"));
        public static final StreamCodec<ByteBuf, BorderSyncPayload> CODEC = StreamCodec.composite(BIG, BorderSyncPayload::json, BorderSyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Reperes 3D : coin A de la balise, redimensionnement, mise en evidence d'un claim. */
    public record VisualPayload(String json) implements CustomPacketPayload {
        public static final Type<VisualPayload> TYPE = new Type<>(CobblePulseClaims.id("visual"));
        public static final StreamCodec<ByteBuf, VisualPayload> CODEC = StreamCodec.composite(BIG, VisualPayload::json, VisualPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
