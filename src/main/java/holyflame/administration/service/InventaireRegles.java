package holyflame.administration.service;

import holyflame.administration.model.ArticleInventaire;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regles de l'inventaire : libelles affichables et effet d'un mouvement sur un lot.
 *
 * Un article d'inventaire est un lot — « 40 tables-bancs », « 12 ordinateurs » — et non une
 * unite. Ce qui bouge dans la vie d'une ecole, ce sont quelques unites a la fois : trois
 * chaises partent chez le menuisier, deux ordinateurs sont declares irreparables. Ces regles
 * tiennent donc trois compteurs sur un meme lot (en service, en reparation, hors service) et
 * refusent tout mouvement qui les rendrait incoherents, plutot que de rogner en silence :
 * une quantite corrigee sans le dire ferait mentir l'inventaire au moment de l'inspection.
 *
 * Aucune dependance Spring : ces regles se verifient a l'unite, sans base ni contexte.
 */
public final class InventaireRegles {

    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("MOBILIER", "Mobilier");
        CATEGORIES.put("INFORMATIQUE", "Informatique");
        CATEGORIES.put("LIVRE", "Livre");
        CATEGORIES.put("SPORT", "Sport");
        CATEGORIES.put("MATERIEL_BUREAU", "Materiel de bureau");
        CATEGORIES.put("AUTRE", "Autre");
    }

    /** Qualite generale du lot. Les indisponibilites se comptent a part (voir les compteurs). */
    public static final Map<String, String> ETATS = new LinkedHashMap<>();
    static {
        ETATS.put("NEUF", "Neuf");
        ETATS.put("BON_ETAT", "Bon etat");
        ETATS.put("USE", "Use");
    }

    public static final String ENTREE = "ENTREE";
    public static final String SORTIE = "SORTIE";
    public static final String REPARATION = "REPARATION";
    public static final String RETOUR_REPARATION = "RETOUR_REPARATION";
    public static final String HORS_SERVICE = "HORS_SERVICE";

    public static final Map<String, String> MOUVEMENTS = new LinkedHashMap<>();
    static {
        MOUVEMENTS.put(ENTREE, "Entree (acquisition, don recu)");
        MOUVEMENTS.put(SORTIE, "Sortie (reforme, don, perte, vol)");
        MOUVEMENTS.put(REPARATION, "Depart en reparation");
        MOUVEMENTS.put(RETOUR_REPARATION, "Retour de reparation");
        MOUVEMENTS.put(HORS_SERVICE, "Mise hors service (irreparable)");
    }

    public static String libelleCategorie(String code) { return libelle(CATEGORIES, code); }
    public static String libelleEtat(String code)      { return libelle(ETATS, code); }
    public static String libelleMouvement(String code) { return libelle(MOUVEMENTS, code); }

    private static String libelle(Map<String, String> table, String code) {
        if (code == null || code.isBlank()) return "—";
        // Un code inconnu (donnee ancienne, code retire du menu) s'affiche tel quel plutot que
        // de disparaitre : mieux vaut un libelle laid qu'une ligne d'inventaire muette.
        return table.getOrDefault(code, code);
    }

    /**
     * Applique un mouvement au lot, ou refuse.
     *
     * @throws IllegalArgumentException si la quantite demandee n'est pas disponible ; le
     *         message est destine a l'utilisateur.
     */
    public static void appliquer(ArticleInventaire a, String type, int quantite) {
        if (quantite <= 0) {
            throw new IllegalArgumentException("La quantite d'un mouvement doit etre d'au moins 1.");
        }
        int enService = a.getQuantiteEnService();

        switch (type == null ? "" : type) {
            case ENTREE -> a.setQuantite(a.getQuantite() + quantite);

            case SORTIE -> {
                // Ce qui quitte l'ecole part d'abord de ce qui est deja reforme : on se debarrasse
                // du materiel casse avant de ceder du materiel qui sert encore.
                int disponible = enService + a.getQuantiteHorsService();
                exiger(quantite <= disponible, "Sortie impossible : " + disponible
                    + " unite(s) seulement peuvent quitter l'inventaire. Les unites en reparation"
                    + " ne sont pas sur place ; enregistrez d'abord leur retour.");
                int depuisHorsService = Math.min(quantite, a.getQuantiteHorsService());
                a.setQuantiteHorsService(a.getQuantiteHorsService() - depuisHorsService);
                a.setQuantite(a.getQuantite() - quantite);
            }

            case REPARATION -> {
                exiger(quantite <= enService, "Depart en reparation impossible : "
                    + enService + " unite(s) seulement sont en service.");
                a.setQuantiteEnReparation(a.getQuantiteEnReparation() + quantite);
            }

            case RETOUR_REPARATION -> {
                exiger(quantite <= a.getQuantiteEnReparation(), "Retour impossible : "
                    + a.getQuantiteEnReparation() + " unite(s) seulement sont en reparation.");
                a.setQuantiteEnReparation(a.getQuantiteEnReparation() - quantite);
            }

            case HORS_SERVICE -> {
                // On reforme en priorite ce qui etait parti en reparation : c'est le cas courant,
                // l'atelier rend le verdict et l'unite ne revient jamais en service.
                int reformables = enService + a.getQuantiteEnReparation();
                exiger(quantite <= reformables, "Mise hors service impossible : "
                    + reformables + " unite(s) seulement peuvent l'etre.");
                int depuisReparation = Math.min(quantite, a.getQuantiteEnReparation());
                a.setQuantiteEnReparation(a.getQuantiteEnReparation() - depuisReparation);
                a.setQuantiteHorsService(a.getQuantiteHorsService() + quantite);
            }

            default -> throw new IllegalArgumentException("Type de mouvement inconnu : " + type);
        }
    }

    private static void exiger(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private InventaireRegles() {}
}
