package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.Parcours;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.ElementConstitutifRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.ParcoursRepository;
import holyflame.administration.repository.UniteEnseignementRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.ReleveSemestrielService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Releve de notes semestriel — l'ecran que le regime universitaire promettait sans le fournir.
 *
 * Il n'invente aucune donnee : il suit la chaine posee par la maquette. La classe dit quel
 * parcours l'etudiant suit, le semestre choisi dit quelles unites le concernent, chaque
 * element constitutif dit quelle matiere porte ses notes. Quand un maillon manque — une
 * classe sans parcours, un element sans matiere — l'ecran le nomme au lieu de rendre un
 * releve incomplet qui passerait pour exact.
 */
@Controller
@RequestMapping("/releve-notes")
public class ReleveNotesController {

    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private ParcoursRepository parcoursRepository;
    @Autowired private UniteEnseignementRepository uniteRepository;
    @Autowired private ElementConstitutifRepository elementRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private ReleveSemestrielService releveService;

    @GetMapping
    public String choisir(@RequestParam(required = false) Long classeId,
                          @RequestParam(defaultValue = "1") int semestre,
                          Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Etablissement etab = etablissementService.getCurrentEtablissement();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());

        List<Classe> classes = etabId != null
            ? classeRepository.findByEtablissementId(etabId) : List.of();
        model.addAttribute("classes", classes);
        model.addAttribute("classeId", classeId);
        model.addAttribute("semestre", semestre);
        model.addAttribute("nbSemestres", nbSemestresMax(etabId));

        if (classeId == null) return "releve-notes";

        Classe classe = classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable."));
        model.addAttribute("classe", classe);

        // Sans parcours, la classe ne renvoie a aucune unite d'enseignement : le releve serait
        // vide sans qu'on sache pourquoi. On le dit, et on indique ou le rattacher.
        if (classe.getParcoursId() == null) {
            model.addAttribute("sansParcours", true);
            return "releve-notes";
        }

        Parcours parcours = parcoursRepository.findById(classe.getParcoursId()).orElse(null);
        model.addAttribute("parcours", parcours);

        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        model.addAttribute("eleves", eleves);

        List<UniteEnseignement> unites =
            uniteRepository.findByParcoursIdAndSemestreOrderByIntituleAsc(classe.getParcoursId(), semestre);
        model.addAttribute("unites", unites);

        // La maquette est la meme pour toute la classe : la charger une fois, et non a chaque
        // etudiant. Sans cela, une promotion de cinquante etudiants declenchait plusieurs
        // centaines de requetes pour relire les memes elements et les memes intitules.
        List<ElementConstitutif> elements = elementsDe(unites);
        Map<Long, String> matieres = nomsDesMatieres(elements);
        model.addAttribute("elementsSansMatiere",
            elements.stream().filter(e -> e.getMatiereId() == null).toList());

        model.addAttribute("releves", eleves.stream()
            .map(e -> calculer(etab, e, semestre, unites, elements, matieres))
            .toList());
        return "releve-notes";
    }

    @GetMapping("/{eleveId}")
    public String releveDUnEtudiant(@PathVariable Long eleveId,
                                    @RequestParam(defaultValue = "1") int semestre,
                                    Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Etablissement etab = etablissementService.getCurrentEtablissement();

        Eleve eleve = eleveRepository.findById(eleveId)
            .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etudiant introuvable."));

        Classe classe = eleve.getClasse();
        if (classe == null || classe.getParcoursId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Cet etudiant n'appartient a aucune classe rattachee a un parcours.");
        }

        List<UniteEnseignement> unites =
            uniteRepository.findByParcoursIdAndSemestreOrderByIntituleAsc(classe.getParcoursId(), semestre);

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("eleve", eleve);
        model.addAttribute("classe", classe);
        model.addAttribute("parcours", parcoursRepository.findById(classe.getParcoursId()).orElse(null));
        model.addAttribute("semestre", semestre);
        model.addAttribute("nbSemestres", nbSemestresMax(etabId));
        model.addAttribute("nomEtab", etab != null && etab.getNom() != null ? etab.getNom() : "");
        model.addAttribute("anneeScolaire", etablissementService.getAnneeScolaireActive());
        List<ElementConstitutif> elements = elementsDe(unites);
        model.addAttribute("releve",
            calculer(etab, eleve, semestre, unites, elements, nomsDesMatieres(elements)));
        return "releve-notes-etudiant";
    }

    // ── Calcul ──────────────────────────────────────────────────────────

    /** La maquette — unites, elements, matieres — est fournie deja chargee : elle ne depend
     *  pas de l'etudiant, seules ses notes changent d'un releve a l'autre. */
    private ReleveSemestrielService.Releve calculer(Etablissement etab, Eleve eleve, int semestre,
                                                    List<UniteEnseignement> unites,
                                                    List<ElementConstitutif> elements,
                                                    Map<Long, String> matieres) {
        List<Note> notes = noteRepository.findByEleveAndAnneeScolaire(
            eleve, etablissementService.getAnneeScolaireActive());

        return releveService.calculer(etab, eleve, semestre, unites, elements, notes, matieres);
    }

    private List<ElementConstitutif> elementsDe(List<UniteEnseignement> unites) {
        List<ElementConstitutif> elements = new ArrayList<>();
        for (UniteEnseignement ue : unites) {
            elements.addAll(elementRepository.findByUniteEnseignementIdOrderByIntituleAsc(ue.getId()));
        }
        return elements;
    }

    private Map<Long, String> nomsDesMatieres(List<ElementConstitutif> elements) {
        List<Long> ids = elements.stream()
            .map(ElementConstitutif::getMatiereId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (ids.isEmpty()) return Map.of();
        return matiereRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Matiere::getId, Matiere::getNom, (a, b) -> a, LinkedHashMap::new));
    }

    /** Le plus long parcours de l'etablissement decide du nombre de semestres proposes. */
    private int nbSemestresMax(Long etabId) {
        if (etabId == null) return 6;
        return parcoursRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etabId).stream()
            .map(Parcours::getNbSemestres)
            .filter(n -> n != null)
            .max(Integer::compareTo)
            .orElse(6);
    }
}
