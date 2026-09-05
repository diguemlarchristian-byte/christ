package holyflame.administration.controller;

import holyflame.administration.controller.InscriptionEcoleController.DonneesInscription;
import holyflame.administration.service.CreationEcoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creation d'une ecole en un seul ecran.
 *
 * L'assistant d'inscription complet (/inscription-ecole) demande une vingtaine de reglages
 * repartis sur quatre ecrans — systeme de notation, seuil d'assiduite, calcul des retards,
 * logo, couleur, langue... — avant de livrer quoi que ce soit. Pour un directeur qui veut
 * simplement ouvrir son etablissement, c'est un mur.
 *
 * Cet ecran ne demande que ce qu'on ne peut pas deviner : le nom de l'ecole, son type, et
 * qui l'administre. Tout le reste prend une valeur par defaut raisonnable, modifiable ensuite
 * dans Parametres. L'assistant detaille reste disponible pour qui veut tout regler d'emblee.
 */
@Controller
public class DemarrageRapideController {

    @Autowired private CreationEcoleService creationEcoleService;

    /**
     * Types d'ecole proposes : code -> {libelle, description, niveaux crees}.
     *
     * C'est le coeur du "sans stress" : au lieu de cocher les niveaux un par un dans une liste
     * de vingt-deux cases, on choisit "Ecole primaire" et les six classes CP1 a CM2 sont creees.
     */
    public static final Map<String, TypeEcole> TYPES = new LinkedHashMap<>();

    public record TypeEcole(String libelle, String description, String icone, List<String> niveaux) {}

    static {
        TYPES.put("MATERNELLE", new TypeEcole("Maternelle", "Petite, moyenne et grande section", "child_care",
            List.of("MATERNELLE_PS", "MATERNELLE_MS", "MATERNELLE_GS")));
        TYPES.put("PRIMAIRE", new TypeEcole("Ecole primaire", "Du CP1 au CM2", "backpack",
            List.of("PRIMAIRE_CP1", "PRIMAIRE_CP2", "PRIMAIRE_CE1", "PRIMAIRE_CE2", "PRIMAIRE_CM1", "PRIMAIRE_CM2")));
        TYPES.put("COLLEGE", new TypeEcole("College", "De la 6eme a la 3eme", "school",
            List.of("COLLEGE_6E", "COLLEGE_5E", "COLLEGE_4E", "COLLEGE_3E")));
        TYPES.put("LYCEE", new TypeEcole("Lycee", "Seconde, premiere et terminale", "history_edu",
            List.of("LYCEE_SECONDE", "LYCEE_PREMIERE", "LYCEE_TERMINALE")));
        TYPES.put("COLLEGE_LYCEE", new TypeEcole("College et lycee", "De la 6eme a la terminale", "account_balance",
            List.of("COLLEGE_6E", "COLLEGE_5E", "COLLEGE_4E", "COLLEGE_3E",
                    "LYCEE_SECONDE", "LYCEE_PREMIERE", "LYCEE_TERMINALE")));
        TYPES.put("GROUPE_SCOLAIRE", new TypeEcole("Groupe scolaire complet", "De la maternelle a la terminale", "apartment",
            List.of("MATERNELLE_PS", "MATERNELLE_MS", "MATERNELLE_GS",
                    "PRIMAIRE_CP1", "PRIMAIRE_CP2", "PRIMAIRE_CE1", "PRIMAIRE_CE2", "PRIMAIRE_CM1", "PRIMAIRE_CM2",
                    "COLLEGE_6E", "COLLEGE_5E", "COLLEGE_4E", "COLLEGE_3E",
                    "LYCEE_SECONDE", "LYCEE_PREMIERE", "LYCEE_TERMINALE")));
        TYPES.put("SUPERIEUR", new TypeEcole("Enseignement superieur", "Licence, master, doctorat", "workspace_premium",
            List.of("SUPERIEUR_L1", "SUPERIEUR_L2", "SUPERIEUR_L3",
                    "SUPERIEUR_M1", "SUPERIEUR_M2", "SUPERIEUR_DOCTORAT")));
    }

    @GetMapping("/demarrer")
    public String formulaire(Model model) {
        model.addAttribute("types", TYPES);
        return "demarrage-rapide";
    }

    @PostMapping("/demarrer")
    public String creer(@RequestParam String nomEcole,
                        @RequestParam String typeEcole,
                        @RequestParam String adminNomComplet,
                        @RequestParam String adminEmail,
                        @RequestParam(required = false) String telephone,
                        Model model,
                        RedirectAttributes ra) {

        TypeEcole type = TYPES.get(typeEcole);
        if (type == null) {
            return reafficher(model, "Choisissez le type de votre etablissement.",
                nomEcole, typeEcole, adminNomComplet, adminEmail, telephone);
        }

        DonneesInscription donnees = new DonneesInscription();
        donnees.nom = nomEcole;
        donnees.categorie = typeEcole;
        donnees.telephone = telephone;
        donnees.niveauxSelectionnes = new ArrayList<>(type.niveaux());
        // Libelle lisible du type d'etablissement, tel qu'il apparaitra dans les parametres.
        donnees.niveaux = type.libelle();
        // Tout le reste (notation, assiduite, couleur, langue, dates de session) reste nul :
        // CreationEcoleService applique les memes valeurs par defaut que l'assistant complet.

        CreationEcoleService.EcoleCreee resultat;
        try {
            resultat = creationEcoleService.creer(donnees, adminNomComplet, adminEmail, "ADMIN");
        } catch (CreationEcoleService.CreationRefusee e) {
            return reafficher(model, e.getMessage(), nomEcole, typeEcole, adminNomComplet, adminEmail, telephone);
        }

        ra.addFlashAttribute("codeAcces", resultat.codeAcces());
        ra.addFlashAttribute("motDePasse", resultat.motDePasse());
        ra.addFlashAttribute("adminEmail", resultat.adminEmail());
        ra.addFlashAttribute("nomEcole", resultat.nomEcole());
        ra.addFlashAttribute("nbClassesCreees", resultat.nbClassesCreees());
        return "redirect:/inscription-ecole/confirmation";
    }

    /** Reaffiche le formulaire en conservant la saisie : la reprise a zero est le pire du "stress". */
    private String reafficher(Model model, String erreur, String nomEcole, String typeEcole,
                              String adminNomComplet, String adminEmail, String telephone) {
        model.addAttribute("types", TYPES);
        model.addAttribute("erreur", erreur);
        model.addAttribute("nomEcole", nomEcole);
        model.addAttribute("typeEcole", typeEcole);
        model.addAttribute("adminNomComplet", adminNomComplet);
        model.addAttribute("adminEmail", adminEmail);
        model.addAttribute("telephone", telephone);
        return "demarrage-rapide";
    }
}
