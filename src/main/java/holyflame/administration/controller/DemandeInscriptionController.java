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

import java.util.Map;
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
    @Autowired private holyflame.administration.service.EtudeDossierService etudeDossier;
    @Autowired private holyflame.administration.repository.CritereAdmissionRepository critereRepository;

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
        model.addAttribute("listeDAttente",
            etudeDossier.listeDAttente(etabId, etablissementService.getAnneeScolaireActive()));
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
        model.addAttribute("dossier", etudeDossier.dossier(d));
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

    // ── La grille d'admission ───────────────────────────────────────────
    //
    // Elle se regle la ou l'on s'en sert, et non dans les parametres generaux : celui qui
    // fait passer les entretiens est celui qui sait ce qu'il faut noter, et il n'a pas
    // forcement acces au reste de l'administration.

    @GetMapping("/criteres")
    public String criteres(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("criteres", critereRepository.findByEtablissementIdOrderByOrdreAsc(etabId));
        return "secretariat-criteres-admission";
    }

    @PostMapping("/criteres")
    public String ajouterCritere(@RequestParam String libelle,
                                 @RequestParam(required = false) String precision,
                                 @RequestParam(required = false) Double poids,
                                 RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (libelle == null || libelle.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "Donnez un intitulé au critère.");
            return "redirect:/secretariat/demandes/criteres";
        }

        var existants = critereRepository.findByEtablissementIdOrderByOrdreAsc(etabId);
        var c = new holyflame.administration.model.CritereAdmission();
        c.setEtablissementId(etabId);
        c.setLibelle(libelle.trim());
        c.setPrecision(precision == null || precision.isBlank() ? null : precision.trim());
        // Un poids nul ou negatif retirerait le critere du calcul tout en le laissant a
        // l'ecran : le jury noterait une ligne qui ne compte pas.
        c.setPoids(poids != null && poids > 0 ? poids : 1.0);
        c.setOrdre(existants.size());
        critereRepository.save(c);

        journalService.log("CRITERE_ADMISSION_AJOUTE", JOURNAL_MODULE, c.getLibelle());
        ra.addFlashAttribute("successMsg", "Critère ajouté à la grille.");
        return "redirect:/secretariat/demandes/criteres";
    }

    /**
     * Retire un critere de la grille sans effacer les notes deja portees.
     *
     * Supprimer la ligne effacerait aussi le raisonnement des commissions passees, et une
     * decision qu'on ne peut plus expliquer est une decision indefendable. Le critere
     * disparait donc des nouvelles grilles, et reste visible sur les dossiers qu'il a servi
     * a noter.
     */
    @PostMapping("/criteres/{critereId}/retirer")
    public String retirerCritere(@PathVariable Long critereId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        var c = critereRepository.findById(critereId)
            .filter(x -> etabId != null && etabId.equals(x.getEtablissementId()))
            .orElse(null);
        if (c == null) {
            ra.addFlashAttribute("erreurMsg", "Ce critère est introuvable.");
            return "redirect:/secretariat/demandes/criteres";
        }
        c.setActif(!c.isActif());
        critereRepository.save(c);

        journalService.log(c.isActif() ? "CRITERE_ADMISSION_REMIS" : "CRITERE_ADMISSION_RETIRE",
            JOURNAL_MODULE, c.getLibelle());
        ra.addFlashAttribute("successMsg", c.isActif()
            ? "Critère remis dans la grille."
            : "Critère retiré des nouvelles grilles. Les dossiers déjà notés le gardent.");
        return "redirect:/secretariat/demandes/criteres";
    }

    // ── Etude du dossier ────────────────────────────────────────────────

    /**
     * Enregistre les notes de la grille et recalcule le total.
     *
     * Les notes arrivent sous la forme note_<identifiant du critere> : le formulaire est
     * construit a partir de la grille de l'etablissement, qui n'est connue qu'a
     * l'execution. Un champ nomme d'avance pour chaque critere aurait fige la grille.
     */
    @PostMapping("/{id}/noter")
    public String noter(@PathVariable Long id,
                        @RequestParam Map<String, String> champs,
                        RedirectAttributes ra) {
        DemandeInscription d = demandeDeLEtablissement(id);
        if (d == null) {
            ra.addFlashAttribute("erreurMsg", "Cette demande est introuvable.");
            return "redirect:/secretariat/demandes";
        }

        Map<Long, Double> saisies = new java.util.LinkedHashMap<>();
        List<String> illisibles = new java.util.ArrayList<>();
        for (var e : champs.entrySet()) {
            if (!e.getKey().startsWith("note_")) continue;
            String brut = e.getValue();
            if (brut == null || brut.isBlank()) continue;
            try {
                Long critere = Long.parseLong(e.getKey().substring(5));
                double note = Double.parseDouble(brut.trim().replace(',', '.'));
                if (note < 0 || note > 20) { illisibles.add(brut.trim()); continue; }
                saisies.put(critere, note);
            } catch (NumberFormatException ignore) {
                illisibles.add(brut.trim());
            }
        }

        Double total = etudeDossier.noter(d, saisies);
        journalService.log("DOSSIER_NOTE", JOURNAL_MODULE,
            d.getReference() + " — " + (total == null ? "aucune note" : arrondi(total) + "/20"));

        if (!illisibles.isEmpty()) {
            // Enregistrer en silence ce qu'on a compris et taire le reste donnerait un
            // classement fonde sur des notes que le jury croit avoir saisies.
            ra.addFlashAttribute("erreurMsg", "Notes ignorées car hors de l'échelle 0–20 : "
                + String.join(", ", illisibles));
        } else {
            ra.addFlashAttribute("successMsg", total == null
                ? "Notes effacées."
                : "Dossier noté : " + arrondi(total) + " / 20.");
        }
        return "redirect:/secretariat/demandes/" + id;
    }

    /**
     * Porte la decision de la commission.
     *
     * Admis n'inscrit personne : entre l'admission et l'inscription, une famille change
     * d'avis ou ne reunit pas les frais. C'est la conversion qui cree l'eleve.
     */
    @PostMapping("/{id}/decider")
    public String decider(@PathVariable Long id,
                          @RequestParam String decision,
                          @RequestParam(required = false) String appreciation,
                          RedirectAttributes ra) {
        DemandeInscription d = demandeDeLEtablissement(id);
        if (d == null) {
            ra.addFlashAttribute("erreurMsg", "Cette demande est introuvable.");
            return "redirect:/secretariat/demandes";
        }
        if (!List.of(DemandeInscription.ADMIS, DemandeInscription.LISTE_ATTENTE,
                DemandeInscription.REFUSEE).contains(decision)) {
            ra.addFlashAttribute("erreurMsg", "Cette décision n'existe pas.");
            return "redirect:/secretariat/demandes/" + id;
        }
        if (DemandeInscription.REFUSEE.equals(decision)
                && (appreciation == null || appreciation.isBlank())) {
            // Un refus sans motif ecrit ne se defend ni devant la famille qui rappelle, ni
            // devant le directeur qui demande pourquoi.
            ra.addFlashAttribute("erreurMsg", "Écrivez ce que la commission a retenu avant de refuser.");
            return "redirect:/secretariat/demandes/" + id;
        }

        etudeDossier.decider(d, decision, appreciation, nomDeLUtilisateur());
        journalService.log("DEMANDE_" + decision, JOURNAL_MODULE,
            d.getReference() + " — " + d.getNomComplet());
        ra.addFlashAttribute("successMsg", switch (decision) {
            case DemandeInscription.ADMIS -> "Candidat admis. Il reste à l'inscrire quand la famille se présente.";
            case DemandeInscription.LISTE_ATTENTE -> "Placé en liste d'attente, au rang que lui donne sa note.";
            default -> "Demande refusée.";
        });
        return "redirect:/secretariat/demandes/" + id;
    }

    private String arrondi(double note) {
        return String.format(java.util.Locale.FRANCE, "%.2f", note);
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
