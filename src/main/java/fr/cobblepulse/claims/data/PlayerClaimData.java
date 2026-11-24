package fr.cobblepulse.claims.data;

import java.util.UUID;

/** Donnees d'un joueur : solde de blocs de claim et minuteur du prochain palier (persistant entre les connexions). */
public class PlayerClaimData {
    public UUID uuid;
    public String name = "";
    /** Blocs gagnes passivement (capital de depart compris), plafonnes. */
    public int accrued;
    /** Credits / debits manuels du staff : hors plafond, jamais remis a zero par le cumul passif. */
    public int bonus;
    /** Temps de jeu effectif accumule vers le prochain palier (ms). */
    public long progressMs;
    /** Temps de jeu effectif total (ms), pour information. */
    public long playedMs;
    public long firstJoin;
    public long lastSeen;
    public boolean toolsGiven;

    public int total() {
        return accrued + bonus;
    }
}
