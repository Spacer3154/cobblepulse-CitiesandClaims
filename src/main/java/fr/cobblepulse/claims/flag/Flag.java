package fr.cobblepulse.claims.flag;

import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.TrustLevel;

/**
 * Definition d'une permission de claim (flag).
 *
 * @param kind         PLAYER : action d'un joueur, evaluee selon son niveau de confiance (colonnes Visiteurs / Membres) ;
 *                     ZONE : comportement de la zone (explosions, feu, apparitions, flags admin), une seule valeur.
 * @param memberLevel  niveau de confiance a partir duquel le joueur compte comme « membre » pour ce flag.
 * @param ownerBypass  le proprietaire (et le staff en contournement) n'est pas soumis au flag.
 */
public record Flag(String id, Category category, Kind kind, FlagState defVisitor, FlagState defMember, boolean defEditable,
                   TrustLevel memberLevel, boolean ownerBypass, String label, String description) {

    public enum Kind { PLAYER, ZONE }

    public enum Category {
        A("A — Construction & Blocs"),
        B("B — Conteneurs & Interactions"),
        C("C — Entités & Objets"),
        D("D — Environnement & Déplacement"),
        E("E — Cobblemon"),
        F("F — Admin & Villes");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public boolean isZone() {
        return kind == Kind.ZONE;
    }
}
