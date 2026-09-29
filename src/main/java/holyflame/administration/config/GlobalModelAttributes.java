package holyflame.administration.config;

import holyflame.administration.model.Etablissement;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injecte automatiquement des attributs communs (ex: couleur d'accentuation de
 * l'etablissement) dans le modele de CHAQUE page rendue, sans que chaque
 * controleur ait besoin de le faire lui-meme.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private static final String COULEUR_PAR_DEFAUT = "#00236f";

    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.repository.UtilisateurRepository utilisateurRepository;
    @Autowired private holyflame.administration.service.PreInscriptionService preInscriptionService;

    @ModelAttribute("accentColor")
    public String accentColor() {
        try {
            Etablissement etab = etablissementService.getCurrentEtablissement();
            if (etab != null && etab.getCouleurPrimaire() != null && !etab.getCouleurPrimaire().isBlank()) {
                return etab.getCouleurPrimaire();
            }
        } catch (Exception ignored) {
            // Utilisateur non authentifie (ex: page de login) : couleur par defaut.
        }
        return COULEUR_PAR_DEFAUT;
    }

    /**
     * Vrai lorsque l'etablissement connecte fonctionne en unites d'enseignement et credits.
     *
     * Expose a toutes les pages pour que chacune n'affiche que ce qui a un sens chez elle :
     * une universite n'a pas de trimestres, de bulletins ni de professeur titulaire, et
     * laisser ces reglages visibles laisse croire qu'ils s'appliquent.
     *
     * Faux pour un visiteur non connecte : les pages publiques sont les memes pour tous.
     */
    @ModelAttribute("estUniversite")
    public boolean estUniversite() {
        try {
            Etablissement etab = etablissementService.getCurrentEtablissement();
            return etab != null && etab.estRegimeLMD();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Ce que la personne connectee peut faire, pour que les menus n'affichent que cela.
     *
     * Les menus testaient auparavant le role, parfois double d'un test sur les anciens
     * modules optionnels. Des lors que l'acces se coche fonctionnalite par fonctionnalite,
     * ces tests ne pouvaient plus tomber juste : une case cochee ouvrait la page sans faire
     * apparaitre le lien, et l'utilisateur se retrouvait avec un droit qu'il ne voyait pas.
     *
     * Menus et securite lisent maintenant la meme chose. Dans un gabarit :
     * th:if="${peutFaire.contains('SECRETARIAT')}"
     *
     * Ce n'est qu'un affichage : cacher un lien ne protege rien. La vraie porte reste
     * SecurityConfig, qui verifie exactement le meme ensemble.
     */
    @ModelAttribute("peutFaire")
    public java.util.Set<String> peutFaire() {
        try {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return java.util.Set.of();
            return utilisateurRepository.findByEmail(auth.getName())
                .map(holyflame.administration.service.Fonctionnalites::effectives)
                .orElseGet(java.util.Set::of);
        } catch (Exception ignored) {
            // Page publique, ou compte introuvable : aucun menu a afficher.
            return java.util.Set.of();
        }
    }

    /**
     * Le nombre de demandes de pré-inscription qui attendent une décision.
     *
     * C'est le chiffre qui donne envie d'ouvrir l'écran. Sans lui, personne ne pense à
     * aller voir, et une famille attend un rappel qui ne vient jamais.
     *
     * Il n'est compté que pour les personnes qui peuvent le traiter : inutile d'interroger
     * la base à chaque page d'un enseignant, qui n'y verrait de toute façon aucun menu.
     */
    @ModelAttribute("demandesEnAttente")
    public long demandesEnAttente() {
        try {
            if (!peutFaire().contains(holyflame.administration.service.Fonctionnalites.PREINSCRIPTIONS)) {
                return 0;
            }
            Long etabId = etablissementService.getCurrentEtablissementId();
            return etabId == null ? 0 : preInscriptionService.enAttente(etabId);
        } catch (Exception ignored) {
            // Un compteur absent vaut mieux qu'une page qui ne s'affiche pas.
            return 0;
        }
    }

    /**
     * Vrai des que la personne a une porte d'entree dans les finances.
     *
     * L'entree de menu « Finances » mene a une page a onglets dont chacun suit sa propre
     * fonctionnalite. Ecrire la liste complete dans le gabarit la rendrait illisible et,
     * surtout, la ferait diverger de SecurityConfig des la premiere fonctionnalite ajoutee.
     */
    @ModelAttribute("peutFinance")
    public boolean peutFinance() {
        java.util.Set<String> f = peutFaire();
        return f.contains(holyflame.administration.service.Fonctionnalites.FIN_CAISSE)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_DEPENSES)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_PAIE_PREPARATION)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_PAIE_PAIEMENT)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_BUDGET)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_RAPPORTS)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_FRAIS)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_COMPTABILITE)
            || f.contains(holyflame.administration.service.Fonctionnalites.FIN_SUIVI_FAMILLES);
    }
}
