package fr.cobblepulse.claims.data;

/** Reglages des flags d'administration : meteo et heure figees, plafond de niveau d'arene. */
public class ClaimSettings {
    /** CLEAR, RAIN ou THUNDER (admin.weather_lock). */
    public String weather = "CLEAR";
    /** Heure visuelle figee (admin.time_lock), 6000 = midi, 18000 = minuit. */
    public long time = 6000;
    /** Niveau impose aux Pokemon en combat (admin.level_cap). */
    public int level_cap = 50;
}
