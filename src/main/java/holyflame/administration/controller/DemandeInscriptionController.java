package holyflame.administration.controller;

import holyflame.administration.model.DemandeInscription;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.DemandeInscriptionRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Le tri des demandes deposees par les familles.
 *
 * Une demande n'est pas un eleve : elle est arrivee d'un formulaire public, sans que rien
 * ne soit verifie. Cet ecran sert a la lire, a la classer, et — quand l'ecole donne suite —
 * a ouvrir le parcours d'inscription habituel avec les champs deja remplis.
 *
 * Convertir n'inscrit donc personne tout seul. Les verifications du parcours d'inscription
 * restent les seules qui font foi : c'est la que la classe devient obligatoire, que les
 * pieces se televersent et que l'ecole engage sa responsabilite.
 */
@Controller
@RequestMapping("/secretariat/demandes")
public class DemandeInscriptionController {

    private static final String JOURNAL_MODULE = "Pre-inscriptions";

    @Autowired private DemandeInscriptionRepository depot;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;

    @GetMapping
    public String liste(@RequestParam(required = false) String statut, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<DemandeInscription> demandes = (statut == null || statut.isBlank() || "TOUTES".equals(statut))
            ? depot.findByEtablissementIdOrderByDateDemandeDesc(etabId)
            : depot.findByEtablissementIdAndStatutOrderByDateDemandeDesc(etabId, statut);

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("demandes", demandes);
        model.addAttribute("statut", statut == null || statut.isBlank() ? "TOUTES" : statut);
        model.addAttribute("nbEnAttente", depot.countByEtablissementIdAndStatutIn(etabId,
            List.of(DemandeInscription.NOUVELLE, DemandeInscription.EN_COURS)));
        return "secretariat-demandes";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        DemandeInscription d = demandeDeLEtablissement(id);
        if (d == null) {
            ra.addFlashAttribute("erreurMsg", "Cette demande est introuvable.");
            return "redirect:/secretariat/demandes";
        }
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("demande", d);
        return "secretariat-demande-detail";
    }

    /** Prendre la demande en main, ou y renoncer, en disant pourquoi. */
    @PostMapping("/{id}/statut")
    public String changerStatut(@PathVariable Long id,
                                @RequestParam String nouveauStatut,
                                @RequestParam(required = false) String noteInterne,
                                RedirectAttributes ra) {
        DemandeInscription d = demandeDeLEtablissement(id);
        if (d == null) {
            ra.addFlashAttribute("erreurMsg", "Cette demande est introuvable.");
            return "redirect:/secretariat/demandes";
        }
        if (!List.of(DemandeInscription.NOUVELLE, DemandeInscription.EN_COURS,
                DemandeInscription.REFUSEE).contains(nouveauStatut)) {
            // CONVERTIE ne se pose pas a la main : elle se gagne en inscrivant l'eleve.
            ra.addFlashAttribute("erreurMsg", "Ce statut ne peut pas être posé depuis cet écran.");
            return "redirect:/secretariat/demandes/" + id;
        }
        if (DemandeInscription.REFUSEE.equals(nouveauStatut)
                && (noteInterne == null || noteInterne.isBlank())) {
            // Un refus sans motif ne se defend pas : ni devant la famille qui rappelle, ni
            // devant le directeur qui demande pourquoi.
            ra.addFlashAttribute("erreurMsg", "Indiquez le motif du refus avant d'enregistrer.");
            return "redirect:/secretariat/demandes/" + id;
        }

        d.setStatut(nouveauStatut);
        d.setNoteInterne(noteInterne == null || noteInterne.isBlank() ? d.getNoteInterne() : noteInterne.trim());
        d.setDateTraitement(LocalDateTime.now());
        d.setTraiteePar(nomDeLUtilisateur());
        depot.save(d);

        journalService.log("DEMANDE_" + nouveauStatut, JOURNAL_MODULE,
            d.getReference() + " — " + d.getNomComplet());
        ra.addFlashAttribute("successMsg", "Demande " + d.getReference() + " mise à jour.");
        return "redirect:/secretariat/demandes/" + id;
    }

    /**
     * Ouvre l'inscription avec ce que la famille a deja saisi.
     *
     * La demande n'est marquee convertie qu'ici, avant l'inscription elle-meme : si la
     * secretaire abandonne en cours de route, la demande reste dans la liste des converties
     * sans eleve rattache. C'est visible, et cela se rattrape — l'inverse ne se rattrape pas,
     * car une demande restee « en attente » apres une inscription reussie fait rappeler une
     * famille deja inscrite.
     */
    @PostMapping("/{id}/convertir")
    public String convertir(@PathVariable Long id, jakarta.servlet.http.HttpSession session,
                            RedirectAttributes ra) {
        DemandeInscription d = demandeDeLEtablissement(id);
        if (d == null) {
            ra.addFlashAttribute("erreurMsg", "Cette demande est introuvable.");
            return "redirect:/secretariat/demandes";
        }
        if (DemandeInscription.CONVERTIE.equals(d.getStatut())) {
            ra.addFlashAttribute("erreurMsg",
                "Cette demande a déjà donné lieu à une inscription. Vérifiez la fiche de l'élève "
                + "avant d'en créer une seconde.");
            return "redirect:/secretariat/demandes/" + id;
        }

        d.setStatut(DemandeInscription.CONVERTIE);
        d.setDateTraitement(LocalDateTime.now());
        d.setTraiteePar(nomDeLUtilisateur());
        depot.save(d);

        journalService.log("DEMANDE_CONVERTIE", JOURNAL_MODULE,
            d.getReference() + " — " + d.getNomComplet());
        ra.addFlashAttribute("successMsg",
            "Demande " + d.getReference() + " : renseignez la suite du dossier.");

        // Le parcours d'inscription reprend la main, avec ce que la famille a saisi. Il lit
        // une session et non des parametres d'adresse : on la remplit donc ici, plutot que
        // de rallonger une URL que le parcours ignorerait.
        var donnees = new InscriptionEleveController.DonneesInscriptionEleve();
        donnees.nomComplet = d.getNomComplet();
        donnees.dateNaissance = d.getDateNaissance();
        donnees.genre = genreDepuisSexe(d.getSexe());
        donnees.ecoleProvenance = d.getEcolePrecedente();

        // Le parent qui a depose la demande est place en contact d'urgence : on ne sait pas
        // s'il est le pere ou la mere, et le deviner mettrait un nom au mauvais endroit dans
        // le dossier. La secretaire le deplacera en connaissance de cause.
        donnees.contactUrgenceNom = d.getParentNom();
        donnees.contactUrgenceTelephone = d.getParentTelephone();

        session.setAttribute("inscriptionEleveDonnees", donnees);
        return "redirect:/secretariat/eleves/nouveau/etape-1";
    }

    /**
     * Le formulaire public dit M ou F ; la fiche d'eleve attend MASCULIN ou FEMININ.
     *
     * Les deux valeurs doivent correspondre exactement a celles du menu deroulant de
     * l'etape 1 : une correspondance approximative ne leverait aucune erreur, elle
     * laisserait simplement le champ vide, et la secretaire croirait que la famille
     * n'avait rien renseigne.
     */
    private String genreDepuisSexe(String sexe) {
        if ("M".equals(sexe)) return "MASCULIN";
        if ("F".equals(sexe)) return "FEMININ";
        return null;
    }

    /**
     * La demande, si elle appartient bien a l'etablissement connecte.
     *
     * Sans ce controle, un identifiant devine dans la barre d'adresse ouvrirait la demande
     * d'une autre ecole — nom d'enfant et telephone des parents compris.
     */
    private DemandeInscription demandeDeLEtablissement(Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null) return null;
        return depot.findById(id)
            .filter(d -> etabId.equals(d.getEtablissementId()))
            .orElse(null);
    }

    private String nomDeLUtilisateur() {
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        return u == null ? null : (u.getPrenom() + " " + u.getNom()).trim();
    }
}
