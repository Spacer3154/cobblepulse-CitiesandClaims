package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.integration.ClanBridge;

import java.util.UUID;

/** Relation d'un joueur avec un claim, pour le code couleur (3D, cartes, blocs fantomes). */
public enum Relation {
    /** Vert : claim personnel. */
    OWN(0x3BE36B),
    /** Cyan : claim ou le joueur est membre (/trust). */
    TRUSTED(0x34D8E8),
    /** Bleu : claim d'un membre du meme clan CobblePulse. */
    CLAN(0x3D7BFF),
    /** Orange : claim d'un tiers. */
    OTHER(0xFF8C2E),
    /** Violet : claim d'administration / spawn. */
    ADMIN(0xB45CFF);

    public final int rgb;

    Relation(int rgb) {
        this.rgb = rgb;
    }

    public static Relation of(UUID viewer, Claim c) {
        Claim top = ClaimManager.top(c);
        if (top.isAdmin()) return ADMIN;
        if (viewer != null && viewer.equals(top.owner)) return OWN;
        if (viewer != null && Access.trust(c, viewer).atLeast(TrustLevel.ACCESS)) return TRUSTED;
        if (ClanBridge.sameClan(viewer, top.owner)) return CLAN;
        return OTHER;
    }
}
