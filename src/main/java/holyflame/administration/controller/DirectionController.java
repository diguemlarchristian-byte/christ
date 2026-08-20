package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/direction")
public class DirectionController {

    @Autowired private EtablissementService etablissementService;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EleveRepository eleveRepository;

    @GetMapping("/suivi")
    public String suivi(
            @RequestParam(required = false) String filtreEnseignant,
            @RequestParam(required = false) Long filtreMatiere,
            @RequestParam(required = false) Long filtreClasse,
            Model model) {

        Utilisateur currentUser = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();

        // Contrôle d'accès : ADMIN ou DIRECTEUR direct, ou délégation active + est directeur
        boolean isAdmin = currentUser != null
            && ("ADMIN".equals(currentUser.getRole()) || "DIRECTEUR".equals(currentUser.getRole()));
        if (!isAdmin) {
            boolean delegue = etabId != null && parametreRepository
                .findByCleAndEtablissementId("DELEGATION_DIRECTION", etabId)
                .map(p -> "true".equals(p.getValeur())).orElse(false);
            if (!delegue) return "redirect:/dashboard";

            String email = currentUser != null ? currentUser.getEmail() : "";
            Personnel perso = etabId != null
                ? personnelRepository.findByEmailAndEtablissementId(email, etabId).orElse(null)
                : null;
            if (perso == null || !"DIRECTEUR".equals(perso.getFonction())) {
                return "redirect:/dashboard";
            }
        }

        List<Matiere> matieres = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId);
        List<Classe>  classes  = classeRepository.findByEtablissementId(etabId);
        Map<Long, Matiere> matiereMap = matieres.stream().collect(Collectors.toMap(Matiere::getId, m -> m));
        Map<Long, Classe>  classeMap  = classes.stream().collect(Collectors.toMap(Classe::getId, c -> c));

        model.addAttribute("matieres", matieres);
        model.addAttribute("classes", classes);

        List<EnseignantAutorisation> autorisations = autorisationRepository.findByEtablissementId(etabId);
        List<Map<String, Object>> suiviRows = new ArrayList<>();

        for (EnseignantAutorisation auth : autorisations) {
            Utilisateur enseignant = utilisateurRepository.findById(auth.getEnseignantId()).orElse(null);
            Matiere matiere = matiereMap.get(auth.getMatiereId());
            Classe  classe  = classeMap.get(auth.getClasseId());
            if (matiere == null || classe == null) continue;

            String nomEnseignant = enseignant != null
                ? enseignant.getNom() + " " + enseignant.getPrenom()
                : "(compte supprimé)";

            if (filtreEnseignant != null && !filtreEnseignant.isBlank()
                    && !nomEnseignant.toLowerCase().contains(filtreEnseignant.toLowerCase())) continue;
            if (filtreMatiere != null && !auth.getMatiereId().equals(filtreMatiere)) continue;
            if (filtreClasse  != null && !auth.getClasseId().equals(filtreClasse))   continue;

            List<Note> notes = noteRepository.findTopByMatiereAndClasse(auth.getMatiereId(), auth.getClasseId());
            long nbElevesSaisies = notes.stream()
                .map(n -> n.getEleve() != null ? n.getEleve().getId() : null)
                .filter(Objects::nonNull).distinct().count();
            long totalEleves = eleveRepository.countByClasseId(auth.getClasseId());
            LocalDateTime derniereSaisie = notes.isEmpty() ? null : notes.get(0).getSaisieAt();

            String statut;
            if (notes.isEmpty())                                          statut = "NON_REMPLI";
            else if (totalEleves > 0 && nbElevesSaisies >= totalEleves)  statut = "COMPLET";
            else                                                          statut = "EN_COURS";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("enseignantNom",   nomEnseignant);
            row.put("matiereNom",      matiere.getNom());
            row.put("classeNom",       classe.getNom());
            row.put("derniereSaisie",  derniereSaisie);
            row.put("nbNotes",         notes.size());
            row.put("nbElevesSaisies", nbElevesSaisies);
            row.put("totalEleves",     totalEleves);
            row.put("statut",          statut);
            suiviRows.add(row);
        }

        model.addAttribute("suiviRows", suiviRows);
        model.addAttribute("filtreEnseignant", filtreEnseignant);
        model.addAttribute("filtreMatiere", filtreMatiere);
        model.addAttribute("filtreClasse", filtreClasse);

        return "direction/suivi";
    }
}
