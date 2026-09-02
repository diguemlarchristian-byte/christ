package holyflame.administration.service;

import holyflame.administration.model.Classe;
import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Etat d'avancement de la saisie des notes, couple enseignant/matiere/classe.
 *
 * Partage entre la page Direction (le detail, ligne par ligne) et le tableau de
 * bord (le seul compteur qui interesse le directeur en arrivant : combien de
 * cours n'ont encore aucune note).
 */
@Service
public class SuiviSaisieService {

    public static final String NON_REMPLI = "NON_REMPLI";
    public static final String EN_COURS   = "EN_COURS";
    public static final String COMPLET    = "COMPLET";

    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EleveRepository eleveRepository;

    /** Une ligne par autorisation d'enseignement, filtrable. Les filtres nuls ou vides sont ignores. */
    public List<Map<String, Object>> lignes(Long etabId, String filtreEnseignant, Long filtreMatiere, Long filtreClasse) {
        List<Matiere> matieres = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId);
        List<Classe> classes = classeRepository.findByEtablissementId(etabId);
        Map<Long, Matiere> matiereMap = matieres.stream().collect(Collectors.toMap(Matiere::getId, m -> m));
        Map<Long, Classe> classeMap = classes.stream().collect(Collectors.toMap(Classe::getId, c -> c));

        List<Map<String, Object>> lignes = new ArrayList<>();
        for (EnseignantAutorisation auth : autorisationRepository.findByEtablissementId(etabId)) {
            Matiere matiere = matiereMap.get(auth.getMatiereId());
            Classe classe = classeMap.get(auth.getClasseId());
            if (matiere == null || classe == null) continue;

            Utilisateur enseignant = utilisateurRepository.findById(auth.getEnseignantId()).orElse(null);
            String nomEnseignant = enseignant != null
                ? enseignant.getNom() + " " + enseignant.getPrenom()
                : "(compte supprimé)";

            if (filtreEnseignant != null && !filtreEnseignant.isBlank()
                    && !nomEnseignant.toLowerCase().contains(filtreEnseignant.toLowerCase())) continue;
            if (filtreMatiere != null && !auth.getMatiereId().equals(filtreMatiere)) continue;
            if (filtreClasse != null && !auth.getClasseId().equals(filtreClasse)) continue;

            List<Note> notes = noteRepository.findTopByMatiereAndClasse(auth.getMatiereId(), auth.getClasseId());
            long nbElevesSaisies = notes.stream()
                .map(n -> n.getEleve() != null ? n.getEleve().getId() : null)
                .filter(Objects::nonNull).distinct().count();
            long totalEleves = eleveRepository.countByClasseId(auth.getClasseId());
            LocalDateTime derniereSaisie = notes.isEmpty() ? null : notes.get(0).getSaisieAt();

            String statut;
            if (notes.isEmpty())                                        statut = NON_REMPLI;
            else if (totalEleves > 0 && nbElevesSaisies >= totalEleves) statut = COMPLET;
            else                                                        statut = EN_COURS;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("enseignantNom",   nomEnseignant);
            ligne.put("matiereNom",      matiere.getNom());
            ligne.put("classeNom",       classe.getNom());
            ligne.put("derniereSaisie",  derniereSaisie);
            ligne.put("nbNotes",         notes.size());
            ligne.put("nbElevesSaisies", nbElevesSaisies);
            ligne.put("totalEleves",     totalEleves);
            ligne.put("statut",          statut);
            lignes.add(ligne);
        }
        return lignes;
    }

    /** Nombre de couples enseignant/matiere/classe sans aucune note saisie. */
    public long nbSansAucuneNote(Long etabId) {
        return lignes(etabId, null, null, null).stream()
            .filter(l -> NON_REMPLI.equals(l.get("statut")))
            .count();
    }
}
