package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.Incident;
import holyflame.administration.model.Pointage;
import holyflame.administration.model.Retard;
import holyflame.administration.model.Retenue;
import holyflame.administration.model.Zone;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.IncidentRepository;
import holyflame.administration.repository.PointageRepository;
import holyflame.administration.repository.RetardRepository;
import holyflame.administration.repository.RetenueRepository;
import holyflame.administration.repository.ZoneRepository;
import holyflame.administration.service.EmailService;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/surveillant")
public class SurveillantController {

    @Autowired private ZoneRepository zoneRepository;
    @Autowired private PointageRepository pointageRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private RetenueRepository retenueRepository;
    @Autowired private RetardRepository retardRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private EmailService emailService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;

    // ── Tableau de bord ───────────────────────────────────────────────────
    @GetMapping
    public String tableauDeBord(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDateTime debutJournee = horlogeService.aujourdHui().atStartOfDay();

        List<Pointage> pointagesRecents = pointageRepository.findByEtablissementIdOrderByDateHeureDesc(etabId)
            .stream().limit(8).toList();
        List<Incident> incidentsRecents = incidentRepository.findByEtablissementIdOrderByDateHeureDesc(etabId)
            .stream().limit(5).toList();
        // Les retenues d'aujourd'hui ont leur propre bloc : "a venir" ne garde que les jours suivants.
        List<Retenue> retenuesAVenir = retenueRepository.findAVenir(etabId, horlogeService.aujourdHui())
            .stream()
            .filter(r -> r.getDateRetenue() != null && r.getDateRetenue().isAfter(horlogeService.aujourdHui()))
            .limit(5).toList();
        List<Zone> zones = zoneRepository.findByEtablissementIdOrderByNomAsc(etabId);

        long pointagesAujourdHui = pointageRepository.countByEtablissementIdDepuis(etabId, debutJournee);
        long incidentsAujourdHui = incidentRepository.countByEtablissementIdDepuis(etabId, debutJournee);

        // Les retards sont saisis par le surveillant chaque matin, mais n'apparaissaient nulle part
        // sur son tableau de bord : il devait ouvrir /surveillant/retards pour savoir ou il en etait.
        List<Retard> retardsDuJour = retardRepository
            .findByEtablissementIdAndDate(etabId, horlogeService.aujourdHui());
        model.addAttribute("retardsAujourdHui", retardsDuJour.size());
        model.addAttribute("retardsDuJour", retardsDuJour.stream().limit(6).toList());

        // Une retenue a surveiller ce soir se perdait au milieu des retenues "a venir".
        model.addAttribute("retenuesDuJour", retenueRepository
            .findAVenir(etabId, horlogeService.aujourdHui()).stream()
            .filter(r -> horlogeService.aujourdHui().equals(r.getDateRetenue()))
            .toList());

        model.addAttribute("pointagesRecents", pointagesRecents);
        model.addAttribute("incidentsRecents", incidentsRecents);
        model.addAttribute("retenuesAVenir", retenuesAVenir);
        model.addAttribute("zones", zones);
        model.addAttribute("pointagesAujourdHui", pointagesAujourdHui);
        model.addAttribute("incidentsAujourdHui", incidentsAujourdHui);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-dashboard";
    }

    // ── Présences (pointages manuels) ───────────────────────────────────────
    @GetMapping("/presences")
    public String presences(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("pointages", pointageRepository.findByEtablissementIdOrderByDateHeureDesc(etabId));
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("zones", zoneRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-presences";
    }

    @PostMapping("/presences")
    public String ajouterPresence(@RequestParam Long eleveId, @RequestParam Long zoneId,
                                  @RequestParam String typeEvenement, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        Zone zone = zoneRepository.findById(zoneId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId()) || !etabId.equals(zone.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Élève ou zone introuvable dans cet établissement.");
        }
        anneeScolaireService.verifierModifiable(holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui()), etabId);

        Pointage p = new Pointage();
        p.setEleve(eleve);
        p.setZone(zone);
        p.setTypeEvenement(typeEvenement);
        p.setDateHeure(horlogeService.maintenant());
        p.setAnneeScolaire(holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui()));
        p.setAlerte(zone.isZoneInterdite());
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) p.setEnregistreParId(utilisateur.getId());
        p.setEtablissementId(etabId);
        pointageRepository.save(p);

        if (zone.isZoneInterdite()) {
            ra.addFlashAttribute("alerteMsg", eleve.getPrenom() + " " + eleve.getNom() + " a ete pointe(e) dans une zone interdite : " + zone.getNom() + ".");
        } else {
            ra.addFlashAttribute("successMsg", "Pointage enregistre pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        }
        return "redirect:/surveillant/presences";
    }


    // ── Scan QR (pointage automatique) ──────────────────────────────────────
    @GetMapping("/scan")
    public String scan(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("zones", zoneRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-scan";
    }

    @PostMapping("/scan/pointage")
    @ResponseBody
    public java.util.Map<String, Object> scanPointage(@RequestParam String matricule,
                                                       @RequestParam Long zoneId,
                                                       @RequestParam String typeEvenement) {
        java.util.Map<String, Object> resultat = new java.util.LinkedHashMap<>();
        Long etabId = etablissementService.getCurrentEtablissementId();

        Eleve eleve = eleveRepository.findByMatriculeAndEtablissementId(matricule.trim().toUpperCase(), etabId).orElse(null);
        if (eleve == null) {
            resultat.put("succes", false);
            resultat.put("message", "Aucun eleve ne correspond a ce code (" + matricule + ").");
            return resultat;
        }

        Zone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null || etabId == null || !etabId.equals(zone.getEtablissementId())) {
            resultat.put("succes", false);
            resultat.put("message", "Zone invalide.");
            return resultat;
        }

        String anneeScolaireScan = holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui());
        if (anneeScolaireService.estCloturee(anneeScolaireScan, etabId)) {
            resultat.put("succes", false);
            resultat.put("message", "L'annee scolaire en cours est cloturee : aucun pointage n'est possible.");
            return resultat;
        }

        Pointage p = new Pointage();
        p.setEleve(eleve);
        p.setZone(zone);
        p.setTypeEvenement(typeEvenement);
        p.setDateHeure(horlogeService.maintenant());
        p.setAnneeScolaire(anneeScolaireScan);
        p.setAlerte(zone.isZoneInterdite());
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) p.setEnregistreParId(utilisateur.getId());
        p.setEtablissementId(etabId);
        pointageRepository.save(p);

        resultat.put("succes", true);
        resultat.put("alerte", zone.isZoneInterdite());
        resultat.put("nomEleve", eleve.getPrenom() + " " + eleve.getNom());
        resultat.put("classe", eleve.getClasse() != null ? eleve.getClasse().getNom() : "");
        resultat.put("zone", zone.getNom());
        resultat.put("typeEvenement", typeEvenement);
        return resultat;
    }

    // ── Retards (arrivées tardives) ─────────────────────────────────────────
    @GetMapping("/retards")
    public String retards(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("retards", retardRepository.findByEtablissementIdOrderByDateDesc(etabId));
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-retards";
    }

    @PostMapping("/retards")
    public String ajouterRetard(@RequestParam Long eleveId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime heureArrivee,
                                 @RequestParam(required = false) String motif,
                                 RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
        LocalDate dateRetard = date != null ? date : horlogeService.aujourdHui();
        String anneeScolaireRetard = holyflame.administration.util.AnneeScolaireUtil.pour(dateRetard);
        anneeScolaireService.verifierModifiable(anneeScolaireRetard, etabId);

        Retard r = new Retard();
        r.setEleve(eleve);
        r.setDate(dateRetard);
        r.setAnneeScolaire(anneeScolaireRetard);
        r.setHeureArrivee(heureArrivee);
        r.setMotif(motif);
        r.setSaisieAt(horlogeService.maintenant());
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) r.setSaisieParId(utilisateur.getId());
        retardRepository.save(r);
        ra.addFlashAttribute("successMsg", "Retard enregistre pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/surveillant/retards";
    }

    @PostMapping("/retards/{id}/supprimer")
    public String supprimerRetard(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Retard r = retardRepository.findById(id).orElseThrow();
        if (etabId == null || r.getEleve() == null || !etabId.equals(r.getEleve().getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retard introuvable dans cet établissement.");
        }
        retardRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Retard supprime.");
        return "redirect:/surveillant/retards";
    }

    // ── Incidents ────────────────────────────────────────────────────────
    @GetMapping("/incidents")
    public String incidents(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("incidents", incidentRepository.findByEtablissementIdOrderByDateHeureDesc(etabId));
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-incidents";
    }

    @PostMapping("/incidents")
    public String ajouterIncident(@RequestParam Long eleveId, @RequestParam String description,
                                  @RequestParam String gravite, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
        anneeScolaireService.verifierModifiable(holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui()), etabId);

        Incident inc = new Incident();
        inc.setEleve(eleve);
        inc.setDescription(description);
        inc.setGravite(gravite);
        inc.setStatut("OUVERT");
        inc.setDateHeure(horlogeService.maintenant());
        inc.setAnneeScolaire(holyflame.administration.util.AnneeScolaireUtil.pour(horlogeService.aujourdHui()));
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) inc.setAuteurId(utilisateur.getId());
        inc.setEtablissementId(etabId);
        incidentRepository.save(inc);
        ra.addFlashAttribute("successMsg", "Incident enregistre pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/surveillant/incidents";
    }

    @PostMapping("/incidents/{id}/retenue")
    public String creerRetenueDepuisIncident(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Incident inc = incidentRepository.findById(id).orElseThrow();
        verifierProprietaire(inc, etabId);

        LocalDate dateRetenueAuto = horlogeService.aujourdHui().plusDays(2);
        String anneeScolaireRetenueAuto = holyflame.administration.util.AnneeScolaireUtil.pour(dateRetenueAuto);
        anneeScolaireService.verifierModifiable(anneeScolaireRetenueAuto, etabId);

        Retenue r = new Retenue();
        r.setEleve(inc.getEleve());
        r.setIncident(inc);
        r.setDateRetenue(dateRetenueAuto);
        r.setAnneeScolaire(anneeScolaireRetenueAuto);
        r.setHeureRetenue("16:00");
        r.setSalle("Salle de permanence");
        r.setMotif(inc.getDescription());
        r.setStatutPresence("EN_ATTENTE");
        r.setEtablissementId(etabId);
        retenueRepository.save(r);

        inc.setStatut("RETENUE_PROGRAMMEE");
        incidentRepository.save(inc);

        ra.addFlashAttribute("successMsg", "Retenue programmee pour " + inc.getEleve().getPrenom() + " " + inc.getEleve().getNom() + ". Modifiez la date/heure/salle si besoin.");
        return "redirect:/surveillant/retenues";
    }

    @PostMapping("/incidents/{id}/convoquer")
    public String convoquerParent(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Incident inc = incidentRepository.findById(id).orElseThrow();
        verifierProprietaire(inc, etabId);

        Eleve eleve = inc.getEleve();
        String emailDestinataire = eleve.getPereEmail() != null && !eleve.getPereEmail().isBlank() ? eleve.getPereEmail()
            : eleve.getMereEmail() != null && !eleve.getMereEmail().isBlank() ? eleve.getMereEmail()
            : eleve.getEmailParent();

        boolean envoye = false;
        if (emailDestinataire != null && !emailDestinataire.isBlank()) {
            String corps = "<p>Bonjour,</p>"
                + "<p>Nous vous informons qu'un incident concernant votre enfant <strong>" + eleve.getPrenom() + " " + eleve.getNom() + "</strong> "
                + "necessite votre presence a l'etablissement.</p>"
                + "<p><strong>Description :</strong> " + inc.getDescription() + "</p>"
                + "<p><strong>Gravite :</strong> " + inc.getGravite() + "</p>"
                + "<p>Merci de contacter le secretariat pour convenir d'un rendez-vous.</p>";
            envoye = emailService.envoyer(emailDestinataire, "Convocation — Incident concernant votre enfant", corps);
        }

        inc.setStatut("CONVOQUE");
        incidentRepository.save(inc);

        if (envoye) {
            ra.addFlashAttribute("successMsg", "Email de convocation envoye aux parents de " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        } else {
            ra.addFlashAttribute("erreurMsg", "Incident marque comme convoque, mais l'email n'a pas pu etre envoye (aucune adresse parent valide ou service email indisponible).");
        }
        return "redirect:/surveillant/incidents";
    }

    // ── Retenues ─────────────────────────────────────────────────────────
    @GetMapping("/retenues")
    public String retenues(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("retenues", retenueRepository.findByEtablissementIdOrderByDateAsc(etabId));
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-retenues";
    }

    @PostMapping("/retenues")
    public String ajouterRetenue(@RequestParam Long eleveId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateRetenue,
                                 @RequestParam String heureRetenue, @RequestParam String salle,
                                 @RequestParam String motif, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
        String anneeScolaireRetenue = holyflame.administration.util.AnneeScolaireUtil.pour(dateRetenue);
        anneeScolaireService.verifierModifiable(anneeScolaireRetenue, etabId);

        Retenue r = new Retenue();
        r.setEleve(eleve);
        r.setDateRetenue(dateRetenue);
        r.setAnneeScolaire(anneeScolaireRetenue);
        r.setHeureRetenue(heureRetenue);
        r.setSalle(salle);
        r.setMotif(motif);
        r.setStatutPresence("EN_ATTENTE");
        r.setEtablissementId(etabId);
        retenueRepository.save(r);
        ra.addFlashAttribute("successMsg", "Retenue enregistree pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");
        return "redirect:/surveillant/retenues";
    }

    @PostMapping("/retenues/{id}/statut")
    public String changerStatutRetenue(@PathVariable Long id, @RequestParam String statutPresence, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Retenue r = retenueRepository.findById(id).orElseThrow();
        if (etabId == null || !etabId.equals(r.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retenue introuvable dans cet établissement.");
        }
        r.setStatutPresence(statutPresence);
        retenueRepository.save(r);
        ra.addFlashAttribute("successMsg", "Statut de presence mis a jour.");
        return "redirect:/surveillant/retenues";
    }

    // ── Zones ────────────────────────────────────────────────────────────
    @GetMapping("/zones")
    public String zones(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("zones", zoneRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "surveillant-zones";
    }

    @PostMapping("/zones")
    public String ajouterZone(@RequestParam String nom, @RequestParam(required = false) Boolean zoneInterdite,
                              RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Etablissement introuvable.");
        }
        Zone z = new Zone();
        z.setNom(nom);
        z.setZoneInterdite(Boolean.TRUE.equals(zoneInterdite));
        z.setEtablissementId(etabId);
        zoneRepository.save(z);
        ra.addFlashAttribute("successMsg", "Zone \"" + nom + "\" ajoutee.");
        return "redirect:/surveillant/zones";
    }

    @PostMapping("/zones/{id}/toggle")
    public String toggleZone(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Zone z = zoneRepository.findById(id).orElseThrow();
        if (etabId == null || !etabId.equals(z.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Zone introuvable dans cet établissement.");
        }
        z.setZoneInterdite(!z.isZoneInterdite());
        zoneRepository.save(z);
        ra.addFlashAttribute("successMsg", "Statut de la zone \"" + z.getNom() + "\" mis a jour.");
        return "redirect:/surveillant/zones";
    }

    @PostMapping("/zones/{id}/supprimer")
    public String supprimerZone(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Zone z = zoneRepository.findById(id).orElseThrow();
        if (etabId == null || !etabId.equals(z.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Zone introuvable dans cet établissement.");
        }
        if (pointageRepository.countByZoneId(id) > 0) {
            ra.addFlashAttribute("erreurMsg", "Impossible de supprimer la zone \"" + z.getNom() + "\" : des pointages y sont deja enregistres.");
            return "redirect:/surveillant/zones";
        }
        zoneRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Zone \"" + z.getNom() + "\" supprimee.");
        return "redirect:/surveillant/zones";
    }

    private void verifierProprietaire(Incident incident, Long etabId) {
        if (etabId == null || incident.getEtablissementId() == null || !etabId.equals(incident.getEtablissementId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incident introuvable dans cet établissement.");
        }
    }
}
