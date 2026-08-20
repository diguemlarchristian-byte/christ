package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.CreneauHoraireRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.ProgrammeRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/gestion-classes")
public class ClasseGestionController {

    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ProgrammeRepository programmeRepository;
    @Autowired private CreneauHoraireRepository creneauHoraireRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;

    // Niveaux de reference proposes en complement des niveaux deja utilises par l'etablissement :
    // saisir librement le "Niveau" d'une classe (comme auparavant) faisait qu'un niveau tape avec
    // une orthographe/accent legerement different de celui choisi dans Parametres > Frais Scolaires
    // ne matchait jamais ce frais a l'inscription — parametrage "reussi" mais jamais applique. En
    // proposant une liste fermee (niveaux existants + standards) avec repli "Autre" pour les cas
    // hors norme, on garde la coherence sans bloquer les etablissements aux besoins particuliers.
    private static final List<String> NIVEAUX_STANDARDS = List.of(
        "Petite Section", "Moyenne Section", "Grande Section",
        "CP1", "CP2", "CE1", "CE2", "CM1", "CM2",
        "6ème", "5ème", "4ème", "3ème",
        "2nde", "1ère", "Terminale",
        "2nde G", "1ère G", "Terminale G"
    );

    private void ajouterSuggestionsNiveaux(Model model, Long etabId) {
        List<String> niveauxExistants = classeRepository.findByEtablissementId(etabId).stream()
            .map(Classe::getNiveau)
            .filter(n -> n != null && !n.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        List<String> niveauxStandards = NIVEAUX_STANDARDS.stream()
            .filter(n -> niveauxExistants.stream().noneMatch(e -> e.equalsIgnoreCase(n)))
            .toList();
        model.addAttribute("niveauxExistants", niveauxExistants);
        model.addAttribute("niveauxStandards", niveauxStandards);
    }

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("enseignants", utilisateurRepository.findByRoleAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId));
        ajouterSuggestionsNiveaux(model, etabId);
        return "gestion-classes";
    }

    @GetMapping("/nouveau")
    public String nouveauForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Etablissement etab = etablissementService.getCurrentEtablissement();
        String anneeScolaire = (etab != null && etab.getAnneeScolaire() != null && !etab.getAnneeScolaire().isBlank())
            ? etab.getAnneeScolaire()
            : (horlogeService.aujourdHui().getYear() + "-" + (horlogeService.aujourdHui().getYear() + 1));
        model.addAttribute("enseignants", utilisateurRepository.findByRoleAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId));
        model.addAttribute("anneeScolaire", anneeScolaire);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        ajouterSuggestionsNiveaux(model, etabId);
        return "gestion-classes-nouveau";
    }

    @PostMapping
    public String ajouterClasse(
            @RequestParam String nom,
            @RequestParam(required = false) String niveau,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam(required = false) Long professeurTitulaireId,
            @RequestParam(required = false) Integer capacite,
            @RequestParam(required = false) String salle,
            @RequestParam(required = false) String notes,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Etablissement etab = etablissementService.getCurrentEtablissement();
        String anneeResolue = (anneeScolaire != null && !anneeScolaire.isBlank())
            ? anneeScolaire
            : (etab != null && etab.getAnneeScolaire() != null && !etab.getAnneeScolaire().isBlank()
                ? etab.getAnneeScolaire()
                : (horlogeService.aujourdHui().getYear() + "-" + (horlogeService.aujourdHui().getYear() + 1)));

        anneeScolaireService.verifierModifiable(anneeResolue, etabId);
        if (classeRepository.findByNomIgnoreCaseAndAnneeScolaireAndEtablissementId(nom.trim(), anneeResolue, etabId).isPresent()) {
            ra.addFlashAttribute("erreur", "Une classe nommée \"" + nom.trim() + "\" existe déjà pour l'année " + anneeResolue + ".");
            return "redirect:/gestion-classes/nouveau";
        }
        String salleSaisie = salle != null ? salle.trim() : "";
        if (!salleSaisie.isEmpty()
            && classeRepository.findBySalleIgnoreCaseAndAnneeScolaireAndEtablissementId(salleSaisie, anneeResolue, etabId).isPresent()) {
            ra.addFlashAttribute("erreur", "La salle \"" + salleSaisie + "\" est déjà attribuée à une autre classe pour l'année " + anneeResolue + ".");
            return "redirect:/gestion-classes/nouveau";
        }

        Classe classe = new Classe();
        classe.setNom(nom);
        classe.setNiveau(niveau);
        classe.setAnneeScolaire(anneeResolue);
        classe.setProfesseurTitulaireId(professeurTitulaireId);
        classe.setCapacite(capacite);
        classe.setSalle(salle);
        classe.setNotes(notes);
        classe.setEtablissementId(etabId);
        classeRepository.save(classe);
        return "redirect:/gestion-classes";
    }

    @PostMapping("/{id}/modifier")
    public String modifierClasse(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam(required = false) String niveau,
            @RequestParam(required = false) String anneeScolaire,
            @RequestParam(required = false) Long professeurTitulaireId,
            @RequestParam(required = false) Integer capacite,
            @RequestParam(required = false) String salle,
            @RequestParam(required = false) String notes,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(id).orElseThrow();
        verifierProprietaire(classe, etabId);
        anneeScolaireService.verifierModifiable(classe.getAnneeScolaire(), etabId);
        if (anneeScolaire != null && !anneeScolaire.isBlank()) {
            anneeScolaireService.verifierModifiable(anneeScolaire, etabId);
        }

        boolean nomDejaPris = classeRepository.findByNomIgnoreCaseAndAnneeScolaireAndEtablissementId(nom.trim(), anneeScolaire, etabId)
            .map(autre -> !autre.getId().equals(id))
            .orElse(false);
        if (nomDejaPris) {
            ra.addFlashAttribute("erreur", "Une classe nommée \"" + nom.trim() + "\" existe déjà pour l'année " + anneeScolaire + ".");
            return "redirect:/gestion-classes";
        }
        String salleSaisie = salle != null ? salle.trim() : "";
        boolean salleDejaPrise = !salleSaisie.isEmpty()
            && classeRepository.findBySalleIgnoreCaseAndAnneeScolaireAndEtablissementId(salleSaisie, anneeScolaire, etabId)
                .map(autre -> !autre.getId().equals(id))
                .orElse(false);
        if (salleDejaPrise) {
            ra.addFlashAttribute("erreur", "La salle \"" + salleSaisie + "\" est déjà attribuée à une autre classe pour l'année " + anneeScolaire + ".");
            return "redirect:/gestion-classes";
        }

        classe.setNom(nom);
        classe.setNiveau(niveau);
        classe.setAnneeScolaire(anneeScolaire);
        classe.setProfesseurTitulaireId(professeurTitulaireId);
        classe.setCapacite(capacite);
        classe.setSalle(salle);
        classe.setNotes(notes);
        classeRepository.save(classe);
        return "redirect:/gestion-classes";
    }

    @GetMapping("/{id}/affectation")
    public String affectationForm(@PathVariable Long id, @RequestParam(required = false) String q, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(id).orElseThrow();
        verifierProprietaire(classe, etabId);

        List<Eleve> elevesClasse = eleveRepository.findByClasseIdOrderByNomAsc(id);

        List<Eleve> autresEleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
            .filter(e -> e.getClasse() == null || !id.equals(e.getClasse().getId()))
            .filter(e -> q == null || q.isBlank()
                || e.getNom().toLowerCase().contains(q.toLowerCase())
                || e.getPrenom().toLowerCase().contains(q.toLowerCase())
                || (e.getMatricule() != null && e.getMatricule().toLowerCase().contains(q.toLowerCase())))
            .toList();

        long garcons = elevesClasse.stream().filter(e -> "MASCULIN".equals(e.getGenre())).count();
        long filles = elevesClasse.stream().filter(e -> "FEMININ".equals(e.getGenre())).count();

        model.addAttribute("classe", classe);
        model.addAttribute("elevesClasse", elevesClasse);
        model.addAttribute("autresEleves", autresEleves);
        model.addAttribute("totalAutresEleves", autresEleves.size());
        model.addAttribute("garcons", garcons);
        model.addAttribute("filles", filles);
        model.addAttribute("q", q);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "affectation-eleves";
    }

    @PostMapping("/{id}/affectation")
    public String affectationSave(@PathVariable Long id, @RequestParam(required = false) List<Long> eleveIds, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(id).orElseThrow();
        verifierProprietaire(classe, etabId);

        int nbAffectes = 0;
        if (eleveIds != null) {
            for (Long eleveId : eleveIds) {
                Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
                if (eleve == null || !etabId.equals(eleve.getEtablissementId())) continue;
                eleve.setClasse(classe);
                eleveRepository.save(eleve);
                nbAffectes++;
            }
        }
        ra.addFlashAttribute("success", nbAffectes > 0
            ? nbAffectes + " élève(s) affecté(s) à la classe " + classe.getNom() + "."
            : "Aucun élève sélectionné.");
        return "redirect:/gestion-classes/" + id + "/affectation";
    }

    @Transactional
    @PostMapping("/{id}/supprimer")
    public String supprimerClasse(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(id).orElseThrow();
        verifierProprietaire(classe, etabId);
        anneeScolaireService.verifierModifiable(classe.getAnneeScolaire(), etabId);
        long nbEleves = eleveRepository.countByClasseId(id);
        if (nbEleves > 0) {
            ra.addFlashAttribute("erreur",
                "Impossible de supprimer cette classe : elle contient " + nbEleves
                + " élève(s). Réaffectez ou supprimez-les d'abord depuis le Secrétariat.");
            return "redirect:/gestion-classes";
        }
        programmeRepository.deleteByClasseId(id);
        creneauHoraireRepository.deleteByClasseId(id);
        classeRepository.deleteById(id);
        ra.addFlashAttribute("success", "Classe supprimée avec succès.");
        return "redirect:/gestion-classes";
    }

    private void verifierProprietaire(Classe classe, Long etabId) {
        if (etabId == null || classe.getEtablissementId() == null || !etabId.equals(classe.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet établissement.");
        }
    }
}
