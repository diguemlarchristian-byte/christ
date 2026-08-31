package holyflame.administration.service;

public class MoisClotureException extends RuntimeException {
    private final int mois;
    private final int anneeCivile;

    private static final String[] NOMS_MOIS = {
        "", "janvier", "fevrier", "mars", "avril", "mai", "juin",
        "juillet", "aout", "septembre", "octobre", "novembre", "decembre"
    };

    public MoisClotureException(int mois, int anneeCivile) {
        super("Le mois de " + NOMS_MOIS[mois] + " " + anneeCivile + " est cloture comptablement.");
        this.mois = mois;
        this.anneeCivile = anneeCivile;
    }

    public int getMois() { return mois; }
    public int getAnneeCivile() { return anneeCivile; }
}
