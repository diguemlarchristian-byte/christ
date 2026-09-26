package holyflame.administration.config;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.Fonctionnalites;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reprend, une seule fois, les acces personnalises de l'ancien systeme.
 *
 * Les droits se decidaient auparavant a trois endroits : le role, une liste de « modules
 * optionnels » reservee au Directeur, et une liste de modules financiers reservee au
 * Tresorier et au Comptable. Le registre des fonctionnalites les remplace tous les trois.
 *
 * Sans cette reprise, un etablissement qui met a jour verrait tous ses reglages disparaitre
 * en silence : le Directeur a qui l'on avait confie le secretariat le perdrait, et le
 * Comptable que l'on avait volontairement tenu a l'ecart de la caisse la retrouverait. Le
 * second cas est le plus grave — une restriction qui saute ne previent personne.
 *
 * La conversion ne s'applique qu'aux comptes qui portaient reellement une personnalisation,
 * et jamais deux fois : une fois accesPersonnalise pose, le compte est passe au nouveau
 * systeme et n'est plus touche.
 */
@Component
@Order(20)
public class MigrationAcces implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;

    public MigrationAcces(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Anciens codes du Directeur vers les codes du registre. */
    private static final Map<String, String> MODULES_DIRECTEUR = Map.of(
        "SECRETARIAT", Fonctionnalites.SECRETARIAT,
        "SURVEILLANCE", Fonctionnalites.ABSENCES,
        "INFIRMERIE", Fonctionnalites.INFIRMERIE,
        "MARKETING", Fonctionnalites.MARKETING,
        "INVENTAIRE", Fonctionnalites.INVENTAIRE,
        "COORDINATION", Fonctionnalites.COORDINATION);

    /** Anciens codes financiers vers les codes du registre. */
    private static final Map<String, String> MODULES_FINANCE = Map.of(
        "CAISSE", Fonctionnalites.FIN_CAISSE,
        "DEPENSES", Fonctionnalites.FIN_DEPENSES,
        "PAIE_PREPARATION", Fonctionnalites.FIN_PAIE_PREPARATION,
        "PAIE_PAIEMENT", Fonctionnalites.FIN_PAIE_PAIEMENT,
        "BUDGET_PARAMETRAGE", Fonctionnalites.FIN_BUDGET,
        "RAPPORTS", Fonctionnalites.FIN_RAPPORTS);

    /** Les droits financiers du registre, pour savoir lesquels remplacer. */
    private static final Set<String> TOUS_FINANCIERS = Set.copyOf(MODULES_FINANCE.values());

    @Override
    public void run(String... args) {
        int repris = 0;
        for (Utilisateur u : utilisateurRepository.findAll()) {
            if (u.isAccesPersonnalise()) continue;

            Set<String> converti = null;

            if ("DIRECTEUR".equals(u.getRole()) && !u.getModulesOptionnelsActifs().isEmpty()) {
                // Les modules optionnels s'ajoutaient a ce que le role permettait deja.
                converti = new LinkedHashSet<>(Fonctionnalites.defautsPourRole("DIRECTEUR"));
                for (String ancien : u.getModulesOptionnelsActifs()) {
                    String nouveau = MODULES_DIRECTEUR.get(ancien);
                    if (nouveau != null) converti.add(nouveau);
                }
            } else if (("TRESORIER".equals(u.getRole()) || "COMPTABLE".equals(u.getRole()))
                    && u.isModulesFinancePersonnalises()) {
                // Les modules financiers REMPLACAIENT les droits financiers du role : on
                // retire donc tous les droits financiers par defaut avant de poser ceux qui
                // avaient ete explicitement coches.
                converti = new LinkedHashSet<>(Fonctionnalites.defautsPourRole(u.getRole()));
                converti.removeAll(TOUS_FINANCIERS);
                Set<String> choisis = u.getModulesFinanceActifs();
                if (choisis != null) {
                    for (String ancien : choisis) {
                        String nouveau = MODULES_FINANCE.get(ancien);
                        if (nouveau != null) converti.add(nouveau);
                    }
                }
            }

            if (converti != null) {
                u.setFonctionnalitesActives(converti);
                utilisateurRepository.save(u);
                repris++;
            }
        }
        if (repris > 0) {
            System.out.println("Acces repris depuis l'ancien systeme pour " + repris + " compte(s).");
        }
    }
}
