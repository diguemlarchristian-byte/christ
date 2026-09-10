package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rh")
public class RHController {

    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ContratRepository contratRepository;
    @Autowired private CongeRepository congeRepository;
    @Autowired private SalaireMensuelRepository salaireRepository;
    @Autowired private LigneSalaireRepository ligneSalaireRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired private holyflame.administration.service.JournalService journalService;
    @Autowired private holyflame.administration.service.CloturePaieService cloturePaieService;
    @Autowired private holyflame.administration.service.BulletinPaiePdfService bulletinPaiePdfService;
    @Autowired private holyflame.administration.service.FileStorageService fileStorageService;
    @Autowired private DocumentPersonnelRepository documentPersonnelRepository;
    @Autowired private holyflame.administration.service.ClotureMensuelleService clotureMensuelleService;


    @GetMapping
    public String index() {
        return "redirect:/personnel";
    }

    private Personnel personnelDuMemeEtablissement(Long personnelId) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        if (etabId == null || !etabId.equals(p.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Membre du personnel introuvable dans cet établissement.");
        }
        return p;
    }

    // Retrouve la fiche personnel liee au compte de connexion actuellement authentifie
    // (rattachement par email, cf. PersonnelController.creerCompte).
    private Personnel personnelDeLUtilisateurConnecte() {
        Long etabId = etablissementService.getCurrentEtablissementId();
        var u = etablissementService.getCurrentUtilisateur();
        if (u == null || etabId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Utilisateur non authentifie.");
        }
        return personnelRepository.findByEmailAndEtablissementId(u.getEmail(), etabId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Aucune fiche personnel n'est associee a votre compte. Contactez l'administration."));
    }

    // ===== CONTRATS =====
    @PostMapping("/contrats")
    public String ajouterContrat(
            @RequestParam Long personnelId,
            @RequestParam String typeContrat,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam Double salaireBase,
            @RequestParam(required = false) String notes) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Contrat c = new Contrat();
        c.setPersonnel(personnel);
        c.setTypeContrat(typeContrat); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setSalaireBase(salaireBase); c.setStatut("ACTIF"); c.setNotes(notes);
        contratRepository.save(c);
        journalService.log("CONTRAT_AJOUTE", "RH",
            personnel.getPrenom() + " " + personnel.getNom() + " — " + typeContrat + " (" + salaireBase + " F/mois)");
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-contrats";
    }

    @PostMapping("/contrats/{id}/supprimer")
    public String supprimerContrat(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = contratRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { contratRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-contrats" : "redirect:/personnel";
    }

    // ===== CONGÉS =====
    @PostMapping("/conges")
    public String ajouterConge(
            @RequestParam Long personnelId,
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-conges";
    }

    @PostMapping("/conges/{id}/approuver")
    public String approuverConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("APPROUVE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/refuser")
    public String refuserConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("REFUSE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/supprimer")
    public String supprimerConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { congeRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    // ===== CONGÉS — auto-service enseignant =====
    // Un enseignant demande son propre conge (la fiche personnel est deduite de son
    // compte connecte, jamais transmise par le client) ; l'ADMIN garde la main pour
    // approuver/refuser via les endpoints ci-dessus.
    @PostMapping("/conges/demander")
    public String demanderConge(
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDeLUtilisateurConnecte();
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    @PostMapping("/conges/{id}/annuler")
    public String annulerConge(@PathVariable Long id) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && personnel.getId().equals(c.getPersonnel().getId()))
            .filter(c -> "EN_ATTENTE".equals(c.getStatut()))
            .ifPresent(congeRepository::delete);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    // ===== SALAIRES / BULLETINS DE PAIE =====

    private static final List<String> LIBELLES_GAIN = List.of(
        "Salaire de base", "Prime de responsabilité", "Congé annuel", "Indemnité de fin de contrat");
    private static final double SEUIL_ECART_PAIE_PCT = 20.0;

    // Vue d'ensemble mensuelle : tous les bulletins du mois + le personnel actif pour qui aucun
    // bulletin n'a encore ete etabli, pour que le tresorier traite sa paie sans ouvrir chaque fiche.
    @GetMapping("/salaires")
    public String listeSalaires(@RequestParam(required = false) Integer mois,
                                @RequestParam(required = false) Integer annee,
                                @RequestParam(required = false) String statut,
                                Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate maintenant = horlogeService.aujourdHui();
        int moisFiltre = mois != null ? mois : maintenant.getMonthValue();
        int anneeFiltre = annee != null ? annee : maintenant.getYear();

        List<SalaireMensuel> salairesDuMois = salaireRepository.findByEtablissementId(etabId).stream()
            .filter(s -> s.getMois() == moisFiltre && s.getAnnee() == anneeFiltre)
            .filter(s -> statut == null || statut.isBlank() || statut.equals(s.getStatut()))
            .collect(Collectors.toList());

        java.util.Set<Long> personnelAvecBulletinCeMois = salaireRepository.findByEtablissementId(etabId).stream()
            .filter(s -> s.getMois() == moisFiltre && s.getAnnee() == anneeFiltre)
            .map(s -> s.getPersonnel().getId())
            .collect(java.util.stream.Collectors.toSet());
        List<Personnel> personnelSansBulletin = personnelRepository
            .findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(p -> "ACTIF".equals(p.getStatut()) && !personnelAvecBulletinCeMois.contains(p.getId()))
            .collect(Collectors.toList());

        double totalEnAttente = salairesDuMois.stream().filter(s -> "EN_ATTENTE".equals(s.getStatut()))
            .mapToDouble(s -> s.getNetAPayer() != null ? s.getNetAPayer() : 0).sum();
        double totalPaye = salairesDuMois.stream().filter(s -> "PAYE".equals(s.getStatut()))
            .mapToDouble(s -> s.getNetAPayer() != null ? s.getNetAPayer() : 0).sum();
        long nbEnAttente = salairesDuMois.stream().filter(s -> "EN_ATTENTE".equals(s.getStatut())).count();

        model.addAttribute("salaires", salairesDuMois);
        model.addAttribute("personnelSansBulletin", personnelSansBulletin);
        model.addAttribute("moisFiltre", moisFiltre);
        model.addAttribute("anneeFiltre", anneeFiltre);
        model.addAttribute("statutFiltre", statut);
        model.addAttribute("totalEnAttente", totalEnAttente);
        model.addAttribute("totalPaye", totalPaye);
        model.addAttribute("nbEnAttente", nbEnAttente);
        model.addAttribute("nomsMois", List.of("Janvier","Fevrier","Mars","Avril","Mai","Juin",
            "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"));
        var utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        model.addAttribute("utilisateurConnecte", utilisateurConnecte);
        model.addAttribute("modulesFinanceActifs", holyflame.administration.service.FinanceModules.effectifs(utilisateurConnecte));

        boolean moisCloture = cloturePaieService.estCloture(moisFiltre, anneeFiltre, etabId);
        model.addAttribute("moisCloture", moisCloture);
        model.addAttribute("cloture", cloturePaieService.cloture(moisFiltre, anneeFiltre, etabId));
        model.addAttribute("peutCloturer",
            !moisCloture && cloturePaieService.peutCloturer(salairesDuMois, personnelSansBulletin.size()));
        model.addAttribute("obstacleCloture",
            moisCloture ? null : cloturePaieService.obstacleACloture(salairesDuMois, personnelSansBulletin.size()));
        model.addAttribute("estAdmin", utilisateurConnecte != null && "ADMIN".equals(utilisateurConnecte.getRole()));
        model.addAttribute("moisClos", cloturePaieService.moisClos(etabId, anneeFiltre));
        return "rh-salaires";
    }

    @GetMapping("/salaires/nouveau")
    public String nouveauBulletin(
            @RequestParam Long personnelId,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            Model model) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Long etabId = etablissementService.getCurrentEtablissementId();

        LocalDate maintenant = horlogeService.aujourdHui();
        int moisChoisi = mois != null ? mois : maintenant.getMonthValue();
        int anneeChoisie = annee != null ? annee : maintenant.getYear();
        YearMonth periode = YearMonth.of(anneeChoisie, moisChoisi);

        Contrat contrat = contratRepository.findByPersonnelId(personnelId).stream()
            .filter(c -> "ACTIF".equals(c.getStatut()))
            .max(Comparator.comparing(Contrat::getDateDebut, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);

        Map<String, String> taux = parametreRepository.findByCategorieAndEtablissementIdOrderByCleAsc("PAIE", etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));

        Long anciennete = personnel.getDateEmbauche() != null
            ? Period.between(personnel.getDateEmbauche(), periode.atEndOfMonth()).toTotalMonths()
            : null;

        // Reference de comparaison : le bulletin du mois precedent pour ce meme employe, s'il existe,
        // pour reperer un ecart anormal avant validation (voir le controle dans ajouterSalaire).
        int[] precedent = periodePrecedente(moisChoisi, anneeChoisie);
        SalaireMensuel salairePrecedent = salaireRepository
            .findByPersonnelIdAndMoisAndAnnee(personnelId, precedent[0], precedent[1]).orElse(null);

        model.addAttribute("personnel", personnel);
        model.addAttribute("contrat", contrat);
        model.addAttribute("mois", moisChoisi);
        model.addAttribute("annee", anneeChoisie);
        model.addAttribute("anciennete", anciennete);
        model.addAttribute("taux", taux);
        model.addAttribute("salairePrecedent", salairePrecedent);
        model.addAttribute("periodePrecedenteLabel", precedent[0] + "/" + precedent[1]);
        return "rh-salaire-nouveau";
    }

    // ===== "Lancer la paie du mois" : genere en un clic un brouillon de bulletin pour chaque
    // employe actif encore sans bulletin ce mois — reconduit le bulletin du mois precedent s'il
    // existe (montants identiques), sinon repart du contrat actif + des taux par defaut. L'admin
    // n'a plus qu'a parcourir et ajuster/valider, jamais a ressaisir depuis zero. =====
    private static final String[] NOMS_MOIS = {"janvier","fevrier","mars","avril","mai","juin",
        "juillet","aout","septembre","octobre","novembre","decembre"};

    private String nomDuMois(int mois) {
        return mois >= 1 && mois <= 12 ? NOMS_MOIS[mois - 1] : String.valueOf(mois);
    }

    private String messageMoisClos(int mois, int annee) {
        return "La paie de " + nomDuMois(mois) + " " + annee + " est cloturee : elle ne peut plus"
             + " etre modifiee. Un administrateur peut la rouvrir depuis la vue d'ensemble.";
    }

    /**
     * Cloture la paie du mois affiche, pour tout le personnel.
     *
     * La cloture est une affirmation — « la paie de ce mois est complete » — et n'est donc
     * acceptee que si elle est vraie : tout le personnel actif a un bulletin, et tous sont
     * payes. Cloturer un mois ou il reste un bulletin en attente enfermerait quelqu'un dehors.
     */
    @PostMapping("/salaires/cloturer-mois")
    public String cloturerMois(@RequestParam int mois, @RequestParam int annee, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String retour = "redirect:/rh/salaires?mois=" + mois + "&annee=" + annee;

        if (cloturePaieService.estCloture(mois, annee, etabId)) {
            ra.addFlashAttribute("erreurMsg", "La paie de " + nomDuMois(mois) + " " + annee + " est deja cloturee.");
            return retour;
        }

        List<SalaireMensuel> bulletins = bulletinsDuMois(etabId, mois, annee);
        int sansBulletin = personnelActifSansBulletin(etabId, mois, annee);
        String obstacle = cloturePaieService.obstacleACloture(bulletins, sansBulletin);
        if (obstacle != null) {
            ra.addFlashAttribute("erreurMsg", "Cloture impossible : " + obstacle);
            return retour;
        }

        var auteur = etablissementService.getCurrentUtilisateur();
        String nomAuteur = auteur == null ? "?"
            : ((auteur.getPrenom() != null ? auteur.getPrenom() + " " : "")
               + (auteur.getNom() != null ? auteur.getNom() : "")).trim();
        var cloture = cloturePaieService.cloturer(mois, annee, etabId, nomAuteur, bulletins);

        journalService.log("CLOTURE_PAIE_MOIS", "RH",
            "Paie de " + nomDuMois(mois) + " " + annee + " cloturee — " + cloture.getNbBulletins()
            + " bulletin(s), " + Math.round(cloture.getTotalNet()) + " F net");
        ra.addFlashAttribute("successMsg", "Paie de " + nomDuMois(mois) + " " + annee
            + " cloturee : " + cloture.getNbBulletins() + " bulletin(s) soldes.");
        return retour;
    }

    /**
     * Rouvre un mois clos. Reserve a l'ADMIN, et journalise : revenir sur une paie declaree
     * complete est exactement ce que la cloture est censee rendre visible.
     */
    @PostMapping("/salaires/rouvrir-mois")
    public String rouvrirMois(@RequestParam int mois, @RequestParam int annee,
                              @RequestParam(required = false) String motif, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        String retour = "redirect:/rh/salaires?mois=" + mois + "&annee=" + annee;

        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur == null || !"ADMIN".equals(utilisateur.getRole())) {
            ra.addFlashAttribute("erreurMsg", "Seul un administrateur peut rouvrir une paie cloturee.");
            return retour;
        }
        if (!cloturePaieService.estCloture(mois, annee, etabId)) {
            ra.addFlashAttribute("erreurMsg", "La paie de " + nomDuMois(mois) + " " + annee + " n'est pas cloturee.");
            return retour;
        }

        cloturePaieService.rouvrir(mois, annee, etabId);
        journalService.log("REOUVERTURE_PAIE_MOIS", "RH",
            "Paie de " + nomDuMois(mois) + " " + annee + " rouverte"
            + (motif != null && !motif.isBlank() ? " — " + motif : " — sans motif indique"));
        ra.addFlashAttribute("successMsg", "Paie de " + nomDuMois(mois) + " " + annee + " rouverte.");
        return retour;
    }

    private List<SalaireMensuel> bulletinsDuMois(Long etabId, int mois, int annee) {
        return salaireRepository.findByEtablissementId(etabId).stream()
            .filter(s -> s.getMois() == mois && s.getAnnee() == annee)
            .collect(Collectors.toList());
    }

    private int personnelActifSansBulletin(Long etabId, int mois, int annee) {
        java.util.Set<Long> avecBulletin = bulletinsDuMois(etabId, mois, annee).stream()
            .map(s -> s.getPersonnel().getId())
            .collect(java.util.stream.Collectors.toSet());
        return (int) personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(p -> "ACTIF".equals(p.getStatut()) && !avecBulletin.contains(p.getId()))
            .count();
    }

    @PostMapping("/salaires/lancer-mois")
    public String lancerPaieDuMois(@RequestParam int mois, @RequestParam int annee, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        YearMonth periode = YearMonth.of(annee, mois);
        anneeScolaireService.verifierModifiable(AnneeScolaireUtil.pour(periode.atDay(1)), etabId);
        if (cloturePaieService.estCloture(mois, annee, etabId)) {
            ra.addFlashAttribute("erreurMsg", messageMoisClos(mois, annee));
            return "redirect:/rh/salaires?mois=" + mois + "&annee=" + annee;
        }

        int[] precedent = periodePrecedente(mois, annee);
        Map<String, String> taux = parametreRepository.findByCategorieAndEtablissementIdOrderByCleAsc("PAIE", etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));

        java.util.Set<Long> dejaTraites = salaireRepository.findByEtablissementId(etabId).stream()
            .filter(s -> s.getMois() == mois && s.getAnnee() == annee)
            .map(s -> s.getPersonnel().getId())
            .collect(java.util.stream.Collectors.toSet());

        List<Personnel> aTraiter = personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(p -> "ACTIF".equals(p.getStatut()) && !dejaTraites.contains(p.getId()))
            .collect(Collectors.toList());

        int dupliques = 0, depuisContrat = 0, ignores = 0;
        for (Personnel p : aTraiter) {
            java.util.Optional<SalaireMensuel> bulletinPrecedent =
                salaireRepository.findByPersonnelIdAndMoisAndAnnee(p.getId(), precedent[0], precedent[1]);
            if (bulletinPrecedent.isPresent()) {
                salaireRepository.save(dupliquerBulletin(bulletinPrecedent.get(), p, mois, annee));
                dupliques++;
                continue;
            }
            Contrat contrat = contratRepository.findByPersonnelId(p.getId()).stream()
                .filter(c -> "ACTIF".equals(c.getStatut()))
                .max(Comparator.comparing(Contrat::getDateDebut, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
            if (contrat == null || contrat.getSalaireBase() == null) {
                ignores++;
                continue;
            }
            salaireRepository.save(construireDepuisContratEtTaux(p, mois, annee, contrat.getSalaireBase(), taux));
            depuisContrat++;
        }

        int total = dupliques + depuisContrat;
        journalService.log("PAIE_LANCEE", "RH",
            mois + "/" + annee + " — " + total + " bulletin(s) genere(s) en brouillon ("
            + dupliques + " reconduit(s), " + depuisContrat + " depuis contrat), "
            + ignores + " ignore(s) faute de contrat actif");

        if (total == 0) {
            ra.addFlashAttribute("infoMsg", ignores > 0
                ? ignores + " employe(s) actif(s) sans contrat actif — impossible de generer un brouillon, a traiter manuellement."
                : "Tout le personnel actif a deja un bulletin pour cette periode.");
        } else {
            ra.addFlashAttribute("successMsg", total + " bulletin(s) genere(s) en brouillon — "
                + dupliques + " reconduit(s) depuis le mois precedent, " + depuisContrat + " depuis le contrat actif."
                + (ignores > 0 ? " " + ignores + " employe(s) ignore(s) faute de contrat actif." : "")
                + " Verifiez et validez chaque bulletin avant paiement.");
        }
        return "redirect:/rh/salaires?mois=" + mois + "&annee=" + annee;
    }

    private int[] periodePrecedente(int mois, int annee) {
        return mois == 1 ? new int[]{12, annee - 1} : new int[]{mois - 1, annee};
    }

    private double parseTaux(Map<String, String> taux, String cle) {
        String v = taux.get(cle);
        if (v == null || v.isBlank()) return 0;
        try {
            return Double.parseDouble(v.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Brouillon a partir du salaire de base du contrat et des taux par defaut de l'etablissement —
     * reproduit exactement le calcul fait cote client dans rh-salaire-nouveau.html. */
    private SalaireMensuel construireDepuisContratEtTaux(Personnel personnel, int mois, int annee,
                                                          Double salaireBase, Map<String, String> taux) {
        YearMonth periode = YearMonth.of(annee, mois);
        double gains = salaireBase != null ? salaireBase : 0;

        double tauxCnpsEmploye = parseTaux(taux, "TAUX_CNPS_EMPLOYE");
        double tauxIrpp = parseTaux(taux, "TAUX_IRPP");
        double tauxTaxeApprentissage = parseTaux(taux, "TAUX_TAXE_APPRENTISSAGE");
        double tauxTaxeForfaitaire = parseTaux(taux, "TAUX_TAXE_FORFAITAIRE");
        double tauxCnpsAccident = parseTaux(taux, "TAUX_CNPS_ACCIDENT_TRAVAIL");
        double tauxCnpsAllocFam = parseTaux(taux, "TAUX_CNPS_ALLOCATIONS_FAMILIALES");
        double tauxCnpsPension = parseTaux(taux, "TAUX_CNPS_PENSION_VIEILLESSE");

        double retenueCnps = Math.round(gains * tauxCnpsEmploye / 100.0);
        double netSoumisIrpp = gains - retenueCnps;
        double irpp = Math.round(netSoumisIrpp * tauxIrpp / 100.0);
        double taxeApprentissage = Math.round(netSoumisIrpp * tauxTaxeApprentissage / 100.0);
        double taxeForfaitaire = Math.round(netSoumisIrpp * tauxTaxeForfaitaire / 100.0);
        double cnpsAccident = Math.round(gains * tauxCnpsAccident / 100.0);
        double cnpsAllocFam = Math.round(gains * tauxCnpsAllocFam / 100.0);
        double cnpsPension = Math.round(gains * tauxCnpsPension / 100.0);

        double totalRetenues = retenueCnps + irpp;
        double totalCharges = taxeApprentissage + taxeForfaitaire + cnpsAccident + cnpsAllocFam + cnpsPension;

        SalaireMensuel s = new SalaireMensuel();
        s.setPersonnel(personnel);
        s.setMois(mois); s.setAnnee(annee);
        s.setPeriodeDebut(periode.atDay(1)); s.setPeriodeFin(periode.atEndOfMonth());
        s.setAnneeScolaire(AnneeScolaireUtil.pour(periode.atDay(1)));
        s.setStatut("EN_ATTENTE");
        s.setTotalBrut(gains);
        s.setTotalRetenuesSalariales(totalRetenues);
        s.setTotalChargesPatronales(totalCharges);
        s.setNetAPayer(gains - totalRetenues);

        List<LigneSalaire> lignes = new java.util.ArrayList<>();
        int ordre = 0;
        ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Salaire de base", null, null, gains);
        if (retenueCnps > 0) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "Cotisation CNPS - Employé", gains, tauxCnpsEmploye, retenueCnps);
        if (irpp > 0) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "IRPP sur salaire", netSoumisIrpp, tauxIrpp, irpp);
        if (taxeApprentissage > 0) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe d'apprentissage", netSoumisIrpp, tauxTaxeApprentissage, taxeApprentissage);
        if (taxeForfaitaire > 0) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe forfaitaire", netSoumisIrpp, tauxTaxeForfaitaire, taxeForfaitaire);
        if (cnpsAccident > 0) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS accident de travail", gains, tauxCnpsAccident, cnpsAccident);
        if (cnpsAllocFam > 0) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS allocations familiales", gains, tauxCnpsAllocFam, cnpsAllocFam);
        if (cnpsPension > 0) ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS pension vieillesse", gains, tauxCnpsPension, cnpsPension);
        s.setLignes(lignes);
        return s;
    }

    /** Reconduction d'un mois sur l'autre : reprend integralement les lignes du bulletin precedent. */
    private SalaireMensuel dupliquerBulletin(SalaireMensuel source, Personnel personnel, int mois, int annee) {
        YearMonth periode = YearMonth.of(annee, mois);
        SalaireMensuel s = new SalaireMensuel();
        s.setPersonnel(personnel);
        s.setMois(mois); s.setAnnee(annee);
        s.setPeriodeDebut(periode.atDay(1)); s.setPeriodeFin(periode.atEndOfMonth());
        s.setAnneeScolaire(AnneeScolaireUtil.pour(periode.atDay(1)));
        s.setStatut("EN_ATTENTE");
        s.setTotalBrut(source.getTotalBrut());
        s.setTotalRetenuesSalariales(source.getTotalRetenuesSalariales());
        s.setTotalChargesPatronales(source.getTotalChargesPatronales());
        s.setNetAPayer(source.getNetAPayer());

        List<LigneSalaire> sourceLignes = ligneSalaireRepository.findBySalaireMensuelIdOrderByOrdreAsc(source.getId());
        List<LigneSalaire> lignes = new java.util.ArrayList<>();
        int ordre = 0;
        for (LigneSalaire l : sourceLignes) {
            ordre = ajouterLigne(lignes, s, ordre, l.getSection(), l.getLibelle(), l.getBase(), l.getTaux(), l.getMontant());
        }
        s.setLignes(lignes);
        return s;
    }

    @PostMapping("/salaires")
    public String ajouterSalaire(
            @RequestParam Long personnelId,
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "0") Double salaireBase,
            @RequestParam(defaultValue = "0") Double primeResponsabilite,
            @RequestParam(defaultValue = "0") Double congeAnnuel,
            @RequestParam(defaultValue = "0") Double indemniteFinContrat,
            @RequestParam(defaultValue = "0") Double retenueCnpsEmploye,
            @RequestParam(defaultValue = "0") Double irppSurSalaire,
            @RequestParam(defaultValue = "0") Double chargeTaxeApprentissage,
            @RequestParam(defaultValue = "0") Double chargeTaxeForfaitaire,
            @RequestParam(defaultValue = "0") Double chargeCnpsAccident,
            @RequestParam(defaultValue = "0") Double chargeCnpsAllocFam,
            @RequestParam(defaultValue = "0") Double chargeCnpsPension,
            @RequestParam(required = false) Double tauxCnpsEmploye,
            @RequestParam(required = false) Double tauxIrpp,
            @RequestParam(required = false) Double tauxTaxeApprentissage,
            @RequestParam(required = false) Double tauxTaxeForfaitaire,
            @RequestParam(required = false) Double tauxCnpsAccident,
            @RequestParam(required = false) Double tauxCnpsAllocFam,
            @RequestParam(required = false) Double tauxCnpsPension,
            @RequestParam(defaultValue = "false") boolean activerPrime,
            @RequestParam(defaultValue = "false") boolean activerConge,
            @RequestParam(defaultValue = "false") boolean activerIndemnite,
            @RequestParam(defaultValue = "false") boolean activerCnpsEmploye,
            @RequestParam(defaultValue = "false") boolean activerIrpp,
            @RequestParam(defaultValue = "false") boolean activerTaxeApprentissage,
            @RequestParam(defaultValue = "false") boolean activerTaxeForfaitaire,
            @RequestParam(defaultValue = "false") boolean activerCnpsAccident,
            @RequestParam(defaultValue = "false") boolean activerCnpsAllocFam,
            @RequestParam(defaultValue = "false") boolean activerCnpsPension,
            @RequestParam(defaultValue = "false") boolean confirmerEcart,
            RedirectAttributes ra) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        YearMonth periode = YearMonth.of(annee, mois);
        anneeScolaireService.verifierModifiable(AnneeScolaireUtil.pour(periode.atDay(1)), etablissementService.getCurrentEtablissementId());

        // C'est le trou que la cloture mensuelle vient fermer : sans elle, un bulletin « oublie »
        // pouvait etre etabli des mois plus tard sur une periode que tout le monde croyait soldee.
        if (cloturePaieService.estCloture(mois, annee, etablissementService.getCurrentEtablissementId())) {
            ra.addFlashAttribute("erreurMsg", messageMoisClos(mois, annee));
            return "redirect:/personnel/" + personnelId + "#rh-salaires";
        }

        // Un seul bulletin par employe et par mois : evite un doublon (double soumission du
        // formulaire, retour arriere du navigateur...) qui fausserait la masse salariale et
        // risquerait un double virement si les deux bulletins sont marques payes independamment.
        if (salaireRepository.findByPersonnelIdAndMoisAndAnnee(personnelId, mois, annee).isPresent()) {
            return "redirect:/personnel/" + personnelId + "?erreurSalaire=doublon#rh-salaires";
        }

        double primeEff = activerPrime ? primeResponsabilite : 0;
        double congeEff = activerConge ? congeAnnuel : 0;
        double indemniteEff = activerIndemnite ? indemniteFinContrat : 0;
        double totalBrut = salaireBase + primeEff + congeEff + indemniteEff;

        double retenueCnpsEmployeEff = activerCnpsEmploye ? retenueCnpsEmploye : 0;
        double irppSurSalaireEff = activerIrpp ? irppSurSalaire : 0;
        double netSoumisIrpp = totalBrut - retenueCnpsEmployeEff;
        double totalRetenues = retenueCnpsEmployeEff + irppSurSalaireEff;

        // Detection d'ecart anormal : si le net a payer s'ecarte de plus de 20% du mois precedent
        // pour ce meme employe, on demande une confirmation explicite avant d'enregistrer — filet
        // de securite contre une virgule decalee ou un zero de trop avant qu'il ne parte en virement.
        if (!confirmerEcart) {
            int[] precedent = periodePrecedente(mois, annee);
            SalaireMensuel bulletinPrecedent = salaireRepository
                .findByPersonnelIdAndMoisAndAnnee(personnelId, precedent[0], precedent[1]).orElse(null);
            if (bulletinPrecedent != null && bulletinPrecedent.getNetAPayer() != null && bulletinPrecedent.getNetAPayer() > 0) {
                double netAPayerCalcule = totalBrut - totalRetenues;
                double ecartPct = Math.abs(netAPayerCalcule - bulletinPrecedent.getNetAPayer()) / bulletinPrecedent.getNetAPayer() * 100;
                if (ecartPct > SEUIL_ECART_PAIE_PCT) {
                    ra.addFlashAttribute("erreurAuth", "Le net a payer (" + Math.round(netAPayerCalcule) + " F) s'ecarte de "
                        + Math.round(ecartPct) + " % du mois precedent (" + Math.round(bulletinPrecedent.getNetAPayer())
                        + " F, " + precedent[0] + "/" + precedent[1] + "). Verifiez les montants, puis cochez "
                        + "\"Confirmer malgre l'ecart\" pour enregistrer quand meme.");
                    return "redirect:/rh/salaires/nouveau?personnelId=" + personnelId + "&mois=" + mois + "&annee=" + annee;
                }
            }
        }

        double taxeApprentissageEff = activerTaxeApprentissage ? chargeTaxeApprentissage : 0;
        double taxeForfaitaireEff = activerTaxeForfaitaire ? chargeTaxeForfaitaire : 0;
        double cnpsAccidentEff = activerCnpsAccident ? chargeCnpsAccident : 0;
        double cnpsAllocFamEff = activerCnpsAllocFam ? chargeCnpsAllocFam : 0;
        double cnpsPensionEff = activerCnpsPension ? chargeCnpsPension : 0;
        double totalCharges = taxeApprentissageEff + taxeForfaitaireEff
            + cnpsAccidentEff + cnpsAllocFamEff + cnpsPensionEff;

        SalaireMensuel s = new SalaireMensuel();
        s.setPersonnel(personnel);
        s.setMois(mois); s.setAnnee(annee);
        s.setPeriodeDebut(periode.atDay(1)); s.setPeriodeFin(periode.atEndOfMonth());
        s.setAnneeScolaire(AnneeScolaireUtil.pour(periode.atDay(1)));
        s.setStatut("EN_ATTENTE");
        s.setTotalBrut(totalBrut);
        s.setTotalRetenuesSalariales(totalRetenues);
        s.setTotalChargesPatronales(totalCharges);
        s.setNetAPayer(totalBrut - totalRetenues);

        List<LigneSalaire> lignes = new java.util.ArrayList<>();
        int ordre = 0;
        ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Salaire de base", null, null, salaireBase);
        if (activerPrime) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Prime de responsabilité", null, null, primeResponsabilite);
        if (activerConge) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Congé annuel", null, null, congeAnnuel);
        if (activerIndemnite) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Indemnité de fin de contrat", null, null, indemniteFinContrat);
        if (activerCnpsEmploye) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "Cotisation CNPS - Employé", totalBrut, tauxCnpsEmploye, retenueCnpsEmploye);
        if (activerIrpp) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "IRPP sur salaire", netSoumisIrpp, tauxIrpp, irppSurSalaire);
        if (activerTaxeApprentissage) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe d'apprentissage", netSoumisIrpp, tauxTaxeApprentissage, chargeTaxeApprentissage);
        if (activerTaxeForfaitaire) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe forfaitaire", netSoumisIrpp, tauxTaxeForfaitaire, chargeTaxeForfaitaire);
        if (activerCnpsAccident) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS accident de travail", totalBrut, tauxCnpsAccident, chargeCnpsAccident);
        if (activerCnpsAllocFam) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS allocations familiales", totalBrut, tauxCnpsAllocFam, chargeCnpsAllocFam);
        if (activerCnpsPension) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS pension vieillesse", totalBrut, tauxCnpsPension, chargeCnpsPension);
        s.setLignes(lignes);

        salaireRepository.save(s);
        journalService.log("BULLETIN_PAIE_CREE", "RH",
            personnel.getPrenom() + " " + personnel.getNom() + " — " + mois + "/" + annee
            + " — net a payer " + s.getNetAPayer() + " F");
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-salaires";
    }

    private int ajouterLigne(List<LigneSalaire> lignes, SalaireMensuel s, int ordre,
                              String section, String libelle, Double base, Double taux, Double montant) {
        LigneSalaire l = new LigneSalaire();
        l.setSalaireMensuel(s); l.setSection(section); l.setLibelle(libelle);
        l.setBase(base); l.setTaux(taux); l.setMontant(montant); l.setOrdre(ordre);
        lignes.add(l);
        return ordre + 1;
    }

    @GetMapping("/salaires/{id}/bulletin")
    public String voirBulletin(@PathVariable Long id, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Bulletin introuvable."));
        remplirModeleBulletin(model, s, "/personnel/" + s.getPersonnel().getId());
        return "bulletin-paie";
    }

    // ===== Auto-service : chaque employe consulte ses propres bulletins, sans passer par
    // Admin/Tresorier — reduit les demandes repetees "peux-tu me renvoyer mon bulletin de mars ?" =====
    @GetMapping("/mes-bulletins")
    public String mesBulletins(Model model) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        model.addAttribute("personnel", personnel);
        model.addAttribute("salaires", salaireRepository.findByPersonnelIdOrderByAnneeDescMoisDesc(personnel.getId()));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "mes-bulletins-paie";
    }

    @GetMapping("/mes-bulletins/{id}")
    public String monBulletin(@PathVariable Long id, Model model) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && personnel.getId().equals(sm.getPersonnel().getId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Bulletin introuvable."));
        remplirModeleBulletin(model, s, "/rh/mes-bulletins");
        return "bulletin-paie";
    }

    /**
     * Fige le bulletin au moment ou il devient « paye ».
     *
     * L'archivage ne doit jamais empecher le paiement : le salaire est verse et comptabilise
     * de toute facon, et un disque plein ou un dossier non inscriptible ne peut pas remettre
     * cela en cause. En cas d'echec on le journalise, l'absence d'archive restant visible a
     * l'ecran par le lien de telechargement qui ne s'affiche pas.
     */
    private void archiverLeBulletin(SalaireMensuel s, Personnel personnel) {
        if (s.isArchive()) return;
        try {
            LocalDateTime horodatage = LocalDateTime.now();
            String code = bulletinPaiePdfService.genererCode(s, horodatage);
            byte[] pdf = bulletinPaiePdfService.genererPdf(s, code, horodatage);
            String chemin = fileStorageService.storeBytes(pdf, "pdf", "bulletins-paie");

            s.setArchiveChemin(chemin);
            s.setArchiveHorodatage(horodatage);
            s.setCodeVerification(code);
            salaireRepository.save(s);

            // Le bulletin rejoint les documents de l'employe : c'est la ou on ira le chercher
            // des mois plus tard, sans avoir a savoir qu'il existe une archive de la paie.
            DocumentPersonnel doc = new DocumentPersonnel();
            doc.setPersonnel(personnel);
            doc.setTypeDocument("BULLETIN_PAIE");
            doc.setNomOriginal("Bulletin de paie " + nomDuMois(s.getMois()) + " " + s.getAnnee() + ".pdf");
            doc.setNomFichier(chemin.substring(chemin.lastIndexOf('/') + 1));
            doc.setCheminFichier(chemin);
            doc.setContentType("application/pdf");
            doc.setTaille((long) pdf.length);
            doc.setDateUpload(horodatage);
            documentPersonnelRepository.save(doc);
        } catch (Exception e) {
            journalService.log("ARCHIVE_BULLETIN_ECHEC", "RH",
                personnel.getPrenom() + " " + personnel.getNom() + " — " + s.getMois() + "/" + s.getAnnee()
                + " — le salaire est paye mais le PDF n'a pas pu etre archive : " + e.getMessage());
        }
    }

    /**
     * Telecharge l'archive telle qu'elle a ete produite au paiement — jamais un bulletin
     * recalcule : c'est tout l'interet de l'avoir figee.
     */
    @GetMapping("/salaires/{id}/archive")
    public ResponseEntity<Resource> archiveBulletin(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null
                && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Bulletin introuvable."));
        return reponseArchive(s);
    }

    /** Meme document, cote auto-service : l'employe telecharge sa propre preuve. */
    @GetMapping("/mes-bulletins/{id}/archive")
    public ResponseEntity<Resource> monArchiveBulletin(@PathVariable Long id) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && personnel.getId().equals(sm.getPersonnel().getId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Bulletin introuvable."));
        return reponseArchive(s);
    }

    private ResponseEntity<Resource> reponseArchive(SalaireMensuel s) {
        if (!s.isArchive()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Ce bulletin n'a pas d'archive : elle n'est produite qu'au moment du paiement.");
        }
        try {
            Resource fichier = fileStorageService.loadAsResource(s.getArchiveChemin());
            String nom = "bulletin-paie-" + s.getAnnee() + "-" + String.format("%02d", s.getMois()) + ".pdf";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(fichier);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Archive introuvable sur le disque.");
        }
    }

    /**
     * Verifie qu'un bulletin papier correspond bien a une archive.
     *
     * La recherche se fait dans le seul etablissement courant : un code valide ailleurs ne
     * doit rien reveler ici.
     */
    @GetMapping("/salaires/verifier")
    public String verifierBulletin(@RequestParam(required = false) String code, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("code", code);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());

        if (code != null && !code.isBlank()) {
            SalaireMensuel trouve = salaireRepository.findByCodeVerification(code.trim().toUpperCase())
                .filter(s -> s.getPersonnel() != null && etabId != null
                    && etabId.equals(s.getPersonnel().getEtablissementId()))
                .orElse(null);
            model.addAttribute("bulletin", trouve);
            model.addAttribute("nomMois", trouve != null ? nomDuMois(trouve.getMois()) : null);
            model.addAttribute("recherche", true);
        }
        return "bulletin-paie-verification";
    }

    private void remplirModeleBulletin(Model model, SalaireMensuel s, String retourUrl) {
        Long etabId = s.getPersonnel().getEtablissementId();
        List<LigneSalaire> lignes = ligneSalaireRepository.findBySalaireMensuelIdOrderByOrdreAsc(s.getId());
        Map<String, List<LigneSalaire>> parSection = lignes.stream()
            .collect(Collectors.groupingBy(LigneSalaire::getSection, LinkedHashMap::new, Collectors.toList()));

        Personnel personnel = s.getPersonnel();
        Long anciennete = personnel.getDateEmbauche() != null && s.getPeriodeFin() != null
            ? Period.between(personnel.getDateEmbauche(), s.getPeriodeFin()).toTotalMonths()
            : null;

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));
        Etablissement etab = etablissementService.getCurrentEtablissement();

        model.addAttribute("bulletin", s);
        model.addAttribute("personnel", personnel);
        model.addAttribute("lignesGains", parSection.getOrDefault("GAIN", List.of()));
        model.addAttribute("lignesRetenues", parSection.getOrDefault("RETENUE_SALARIALE", List.of()));
        model.addAttribute("lignesCharges", parSection.getOrDefault("CHARGE_PATRONALE", List.of()));
        model.addAttribute("anciennete", anciennete);
        model.addAttribute("nomEtab", etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        model.addAttribute("adresseEtab", etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        model.addAttribute("telEtab", params.getOrDefault("TELEPHONE_ECOLE", ""));
        model.addAttribute("logoPath", params.getOrDefault("LOGO_ETAB", null));
        model.addAttribute("monnaie", params.getOrDefault("MONNAIE", "FCFA"));
        model.addAttribute("retourUrl", retourUrl);
    }

    // Le passage a « paye » ecrit trois choses : le statut du bulletin et deux depenses. Sans
    // transaction, un echec au milieu laissait un salaire marque paye a moitie comptabilise.
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/salaires/{id}/payer")
    public String payerSalaire(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElse(null);
        if (s == null) return "redirect:/personnel";
        Long pid = s.getPersonnel().getId();
        if ("PAYE".equals(s.getStatut())) {
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }
        anneeScolaireService.verifierModifiable(s.getAnneeScolaire(), etabId);
        if (cloturePaieService.estCloture(s.getMois(), s.getAnnee(), etabId)) {
            ra.addFlashAttribute("erreurMsg", messageMoisClos(s.getMois(), s.getAnnee()));
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }

        // Le paiement d'un salaire ecrit dans les comptes : il ne peut pas viser un mois
        // comptable deja arrete. Seule la cloture d'annee etait verifiee, si bien qu'un salaire
        // paye en juin pouvait encore ajouter une charge a un septembre depuis longtemps clos.
        // MoisClotureException est rattrapee par GlobalExceptionHandler, comme partout ailleurs.
        clotureMensuelleService.verifierModifiable(horlogeService.aujourdHui(), etabId);

        // Deux clics sur « Payer » — une double soumission, un retour arriere, une connexion
        // lente — passaient tous deux ce controle et comptabilisaient le salaire deux fois.
        // Rien ne permettait ensuite de distinguer le doublon d'une seconde depense legitime :
        // la masse salariale etait fausse sans que personne ne puisse le voir.
        if (depenseRepository.existsBySalaireMensuelId(s.getId())) {
            ra.addFlashAttribute("erreurMsg",
                "Ce bulletin a deja ete comptabilise. Aucune ecriture n'a ete ajoutee.");
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }

        s.setStatut("PAYE");
        s.setDatePaiement(horlogeService.aujourdHui());
        salaireRepository.save(s);

        Personnel personnel = s.getPersonnel();
        Contrat contratActif = contratRepository.findByPersonnelId(pid).stream()
            .filter(c -> "ACTIF".equals(c.getStatut()))
            .max(Comparator.comparing(Contrat::getDateDebut, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
        String codeCategorieBrut = contratActif == null ? "66161"
            : switch (contratActif.getTypeContrat()) {
                case "VACATAIRE" -> "66120";
                case "CDI", "CDD" -> "66110";
                default -> "66161";
            };

        String libellePeriode = s.getMois() + "/" + s.getAnnee();
        String nomPersonnel = personnel.getPrenom() + " " + personnel.getNom();

        categorieComptableRepository.findByCodeAndEtablissementId(codeCategorieBrut, etabId).ifPresent(cat -> {
            Depense d = new Depense();
            d.setDesignation("Salaire " + libellePeriode + " - " + nomPersonnel);
            d.setCategorieComptable(cat);
            d.setSens("CHARGE");
            d.setBeneficiaire(nomPersonnel);
            d.setMontant(s.getTotalBrut());
            d.setDateDepense(horlogeService.aujourdHui());
            d.setStatut("PAYE");
            d.setAnneeScolaire(AnneeScolaireUtil.pour(horlogeService.aujourdHui()));
            d.setEtablissementId(etabId);
            d.setSalaireMensuelId(s.getId());
            depenseRepository.save(d);
        });

        if (s.getTotalChargesPatronales() != null && s.getTotalChargesPatronales() > 0) {
            categorieComptableRepository.findByCodeAndEtablissementId("66420", etabId).ifPresent(cat -> {
                Depense d = new Depense();
                d.setDesignation("Charges patronales sur salaire " + libellePeriode + " - " + nomPersonnel);
                d.setCategorieComptable(cat);
                d.setSens("CHARGE");
                d.setBeneficiaire(nomPersonnel);
                d.setMontant(s.getTotalChargesPatronales());
                d.setDateDepense(horlogeService.aujourdHui());
                d.setStatut("PAYE");
                d.setAnneeScolaire(AnneeScolaireUtil.pour(horlogeService.aujourdHui()));
                d.setEtablissementId(etabId);
                d.setSalaireMensuelId(s.getId());
                depenseRepository.save(d);
            });
        }

        archiverLeBulletin(s, personnel);

        journalService.log("SALAIRE_PAYE", "RH",
            nomPersonnel + " — " + libellePeriode + " — " + s.getNetAPayer() + " F net, comptabilise automatiquement"
            + (s.isArchive() ? ", archive sous " + s.getCodeVerification() : ""));
        return "redirect:/personnel/" + pid + "#rh-salaires";
    }

    @PostMapping("/salaires/{id}/supprimer")
    public String supprimerSalaire(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElse(null);
        if (s == null) return "redirect:/personnel";
        Long pid = s.getPersonnel().getId();
        if ("PAYE".equals(s.getStatut())) {
            ra.addFlashAttribute("erreurMsg", "Ce bulletin est déjà payé et a été comptabilisé — il ne peut plus être supprimé.");
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }
        if (cloturePaieService.estCloture(s.getMois(), s.getAnnee(), etabId)) {
            ra.addFlashAttribute("erreurMsg", messageMoisClos(s.getMois(), s.getAnnee()));
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }
        String nomPersonnel = s.getPersonnel().getPrenom() + " " + s.getPersonnel().getNom();
        String libellePeriode = s.getMois() + "/" + s.getAnnee();
        salaireRepository.delete(s);
        journalService.log("BULLETIN_PAIE_SUPPRIME", "RH", nomPersonnel + " — " + libellePeriode);
        return "redirect:/personnel/" + pid + "#rh-salaires";
    }
}
