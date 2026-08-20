package holyflame.administration.service;

import holyflame.administration.model.Utilisateur;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registre des modules financiers activables individuellement pour un compte
 * TRESORIER ou COMPTABLE, avec les droits par defaut de chaque role. Permet a
 * l'ADMIN de decider, etablissement par etablissement, qui fait quoi entre les
 * deux roles (caisse, depenses, paie, budget/parametrage, rapports) — pareil
 * que le mecanisme de modules optionnels du role DIRECTEUR.
 */
public final class FinanceModules {

    public static final String CAISSE = "CAISSE";
    public static final String DEPENSES = "DEPENSES";
    public static final String PAIE_PREPARATION = "PAIE_PREPARATION";
    public static final String PAIE_PAIEMENT = "PAIE_PAIEMENT";
    public static final String BUDGET_PARAMETRAGE = "BUDGET_PARAMETRAGE";
    public static final String RAPPORTS = "RAPPORTS";

    public static final Map<String, String> LIBELLES = new LinkedHashMap<>();
    static {
        LIBELLES.put(CAISSE, "Caisse & encaissements (paiements, reçus, arriérés, rappels, comptage de caisse)");
        LIBELLES.put(DEPENSES, "Dépenses (achats, décaissements)");
        LIBELLES.put(PAIE_PREPARATION, "Préparation de la paie (calcul des bulletins)");
        LIBELLES.put(PAIE_PAIEMENT, "Paiement de la paie (déclenchement du décaissement)");
        LIBELLES.put(BUDGET_PARAMETRAGE, "Budget prévisionnel, plan comptable & taux de paie");
        LIBELLES.put(RAPPORTS, "Rapports & exports financiers");
    }

    public static final Set<String> TOUS = Set.of(CAISSE, DEPENSES, PAIE_PREPARATION, PAIE_PAIEMENT, BUDGET_PARAMETRAGE, RAPPORTS);

    // Le Tresorier avait acces a tout avant la segmentation : ce defaut preserve le
    // comportement existant pour les comptes deja crees, tant que l'ADMIN ne les
    // personnalise pas explicitement depuis Parametres > Roles > Acces aux interfaces.
    private static final Set<String> DEFAUT_TRESORIER = TOUS;
    // Le Comptable est un role neuf : defaut recommande = enregistrement/classement,
    // preparation de la paie, budget/plan comptable et rapports — sans toucher a la caisse.
    private static final Set<String> DEFAUT_COMPTABLE = Set.of(DEPENSES, PAIE_PREPARATION, BUDGET_PARAMETRAGE, RAPPORTS);

    public static Set<String> defautsPourRole(String role) {
        if ("TRESORIER".equals(role)) return DEFAUT_TRESORIER;
        if ("COMPTABLE".equals(role)) return DEFAUT_COMPTABLE;
        return Set.of();
    }

    /** Ensemble effectif des modules financiers accessibles a cet utilisateur. */
    public static Set<String> effectifs(Utilisateur u) {
        if (u == null) return Set.of();
        if ("ADMIN".equals(u.getRole()) || "SUPER_ADMIN".equals(u.getRole())) return TOUS;
        Set<String> personnalises = u.getModulesFinanceActifs();
        if (personnalises != null) return personnalises;
        return defautsPourRole(u.getRole());
    }

    public static boolean autorise(Utilisateur u, String module) {
        return effectifs(u).contains(module);
    }

    private FinanceModules() {}
}
