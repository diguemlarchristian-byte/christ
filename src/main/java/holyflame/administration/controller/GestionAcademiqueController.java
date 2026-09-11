package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.model.Matiere;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gestion-academique")
public class GestionAcademiqueController {

    @Autowired private ClasseRepository classeRepository;


    @Autowired private holyflame.administration.repository.ParcoursRepository parcoursRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index(Model model) {
        // Cet ecran raisonne en classes, matieres a coefficient et professeurs titulaires :
        // aucune de ces notions n'existe en regime universitaire, ou les etudiants suivent des
        // unites d'enseignement a credits. Le menu ne propose plus l'entree, mais l'adresse
        // reste atteignable — mieux vaut renvoyer vers la maquette pedagogique que d'afficher
        // un formulaire de classes et de coefficients qui ne correspond a rien.
        var etablissement = etablissementService.getCurrentEtablissement();
        if (etablissement != null && etablissement.estRegimeLMD()) {
            return "redirect:/academique-universite";
        }

        Long etabId = etablissementService.getCurrentEtablissementId();

        List<Classe> classes = classeRepository.findByEtablissementId(etabId);
        List<Matiere> matieres = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId);
        List<holyflame.administration.model.Utilisateur> enseignants =
            utilisateurRepository.findByRoleAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId);

        // Effectif reel par classe (regroupement en memoire, une seule requete)
        List<Eleve> eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        Map<Long, Long> effectifParClasse = eleves.stream()
            .filter(e -> e.getClasse() != null)
            .collect(Collectors.groupingBy(e -> e.getClasse().getId(), Collectors.counting()));

        // Nom du professeur titulaire resolu cote serveur
        Map<Long, String> nomEnseignantParId = enseignants.stream()
            .collect(Collectors.toMap(holyflame.administration.model.Utilisateur::getId,
                u -> u.getPrenom() + " " + u.getNom(), (a, b) -> a));

        // Nombre reel d'enseignants distincts autorises par matiere
        List<EnseignantAutorisation> autorisations = autorisationRepository.findByEtablissementId(etabId);
        Map<Long, Long> nbEnseignantsParMatiere = autorisations.stream()
            .collect(Collectors.groupingBy(EnseignantAutorisation::getMatiereId,
                Collectors.mapping(EnseignantAutorisation::getEnseignantId, Collectors.toCollection(java.util.LinkedHashSet::new))))
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));

        List<Map<String, Object>> classesVue = new java.util.ArrayList<>();
        for (Classe c : classes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classe", c);
            row.put("effectif", effectifParClasse.getOrDefault(c.getId(), 0L));
            row.put("nomTitulaire", c.getProfesseurTitulaireId() != null
                ? nomEnseignantParId.getOrDefault(c.getProfesseurTitulaireId(), "--") : null);
            classesVue.add(row);
        }

        List<Map<String, Object>> matieresVue = new java.util.ArrayList<>();
        for (Matiere m : matieres) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("matiere", m);
            row.put("nbEnseignants", nbEnseignantsParMatiere.getOrDefault(m.getId(), 0L));
            matieresVue.add(row);
        }

        model.addAttribute("classesVue", classesVue);
        model.addAttribute("matieresVue", matieresVue);
        model.addAttribute("enseignants", enseignants);
        model.addAttribute("nbClasses", classes.size());
        model.addAttribute("nbMatieres", matieres.size());
        // Parcours proposables a une classe en regime universitaire.
        model.addAttribute("parcoursDisponibles", etabId != null
            ? parcoursRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etabId)
            : java.util.List.of());
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "gestion-academique";
    }
}
