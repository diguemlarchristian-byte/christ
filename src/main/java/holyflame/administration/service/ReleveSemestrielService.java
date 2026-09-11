package holyflame.administration.service;

import holyflame.administration.model.ElementConstitutif;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Note;
import holyflame.administration.model.UniteEnseignement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcule le releve de notes semestriel d'un etudiant.
 *
 * C'est la piece qui manquait au regime universitaire. La maquette declarait des unites
 * d'enseignement a credits, les enseignants saisissaient des notes, et rien ne reliait les
 * deux : un element constitutif n'etait qu'un intitule libre, une note portait sur une
 * matiere, et les deux lignes s'ignoraient. Les regles de {@link RegimeAcademiqueService} —
 * acquisition, compensation, mention — etaient donc ecrites, testees, et appelees par rien.
 *
 * La chaine est desormais : eleve → classe → parcours → unite → element constitutif →
 * matiere → notes. Le semestre de l'unite decide quelles matieres comptent ; les notes de
 * l'etudiant dans ces matieres fournissent les valeurs. Aucune inference sur la periode de
 * la note n'est faite : une matiere n'appartient qu'a un semestre du parcours, cela suffit.
 *
 * Le calcul est isole de toute base : il prend les donnees deja chargees et rend le releve.
 */
@org.springframework.stereotype.Service
public class ReleveSemestrielService {

    /** Une unite d'enseignement telle qu'elle figure au releve. */
    public record LigneUnite(UniteEnseignement unite,
                             Double moyenne,
                             RegimeAcademiqueService.ModeObtention obtention,
                             int creditsAcquis,
                             List<LigneElement> elements) {

        /** Une unite sans aucune note ne se juge pas : elle reste en attente. */
        public boolean estEvaluee() { return moyenne != null; }
    }

    /** Un element constitutif et la moyenne de l'etudiant dans sa matiere. */
    public record LigneElement(ElementConstitutif element, String matiere, Double moyenne, int nbNotes) {}

    /** Le releve complet d'un semestre. */
    public record Releve(Eleve eleve,
                         int semestre,
                         List<LigneUnite> unites,
                         Double moyenneSemestre,
                         int creditsAcquis,
                         int creditsPossibles,
                         String mention) {

        public boolean estVide() { return unites.isEmpty(); }

        /** Vrai quand aucune unite du semestre n'a encore recu de note. */
        public boolean sansAucuneNote() {
            return moyenneSemestre == null;
        }
    }

    /**
     * Les deux semestres d'une annee, reunis pour la deliberation.
     *
     * Un jury ne statue pas semestre par semestre : il regarde l'annee. Les credits des deux
     * semestres s'additionnent, et la moyenne annuelle les pondere — un semestre ou l'etudiant
     * a valide trente credits ne pese pas comme un semestre a demi evalue.
     */
    public record BilanAnnuel(Eleve eleve,
                              List<Releve> semestres,
                              Double moyenneAnnuelle,
                              int creditsAcquis,
                              int creditsPossibles) {

        /** Vrai quand aucun des deux semestres n'a recu la moindre note. */
        public boolean sansAucuneNote() { return moyenneAnnuelle == null; }
    }

    /**
     * Reunit des releves deja calcules. La moyenne annuelle pondere chaque semestre par les
     * credits qui y ont ete evalues : un semestre encore vide ne compte pas, et ne tire donc
     * pas l'annee vers le bas.
     */
    public BilanAnnuel bilanAnnuel(Eleve eleve, List<Releve> semestres) {
        double sommePonderee = 0;
        double sommePoids = 0;
        int creditsAcquis = 0;
        int creditsPossibles = 0;

        for (Releve r : semestres) {
            creditsAcquis += r.creditsAcquis();
            creditsPossibles += r.creditsPossibles();
            if (r.moyenneSemestre() == null) continue;

            // Le poids d'un semestre est le nombre de credits qu'il a reellement evalues.
            int evalues = r.unites().stream()
                .filter(LigneUnite::estEvaluee)
                .mapToInt(u -> u.unite().getCredits() != null ? u.unite().getCredits() : 0)
                .sum();
            if (evalues <= 0) continue;
            sommePonderee += r.moyenneSemestre() * evalues;
            sommePoids += evalues;
        }

        Double moyenne = sommePoids > 0 ? sommePonderee / sommePoids : null;
        return new BilanAnnuel(eleve, semestres, moyenne, creditsAcquis, creditsPossibles);
    }

    private final RegimeAcademiqueService regime;

    public ReleveSemestrielService(RegimeAcademiqueService regime) {
        this.regime = regime;
    }

    /**
     * @param unites    les unites du parcours pour ce semestre
     * @param elements  les elements constitutifs de ces unites
     * @param notes     toutes les notes de l'etudiant sur l'annee
     * @param matieres  nom de chaque matiere, par identifiant
     */
    public Releve calculer(Etablissement etablissement, Eleve eleve, int semestre,
                           List<UniteEnseignement> unites,
                           List<ElementConstitutif> elements,
                           List<Note> notes,
                           Map<Long, String> matieres) {

        Map<Long, List<ElementConstitutif>> parUnite = new LinkedHashMap<>();
        for (ElementConstitutif ec : elements) {
            parUnite.computeIfAbsent(ec.getUniteEnseignementId(), k -> new ArrayList<>()).add(ec);
        }

        // Premiere passe : la moyenne de chaque unite. Le statut ne peut pas encore etre
        // arrete, car la compensation depend de la moyenne du semestre, qui depend d'elles.
        List<LigneUnite> provisoire = new ArrayList<>();
        for (UniteEnseignement ue : unites) {
            List<LigneElement> lignes = new ArrayList<>();
            double sommePonderee = 0;
            double sommeCoefficients = 0;

            for (ElementConstitutif ec : parUnite.getOrDefault(ue.getId(), List.of())) {
                Double moyenneEc = moyenneDansLaMatiere(notes, ec.getMatiereId());
                int nb = ec.getMatiereId() == null ? 0 : (int) notes.stream()
                    .filter(n -> n.getMatiere() != null && ec.getMatiereId().equals(n.getMatiere().getId()))
                    .count();
                String nomMatiere = ec.getMatiereId() == null ? null
                    : matieres.getOrDefault(ec.getMatiereId(), "Matiere inconnue");
                lignes.add(new LigneElement(ec, nomMatiere, moyenneEc, nb));

                if (moyenneEc != null) {
                    double coef = ec.getCoefficient() != null ? ec.getCoefficient() : 1.0;
                    sommePonderee += moyenneEc * coef;
                    sommeCoefficients += coef;
                }
            }

            Double moyenneUe = sommeCoefficients > 0 ? sommePonderee / sommeCoefficients : null;
            provisoire.add(new LigneUnite(ue, moyenneUe, null, 0, lignes));
        }

        // La moyenne du semestre pondere les unites par leurs credits : c'est le poids d'une
        // unite dans le diplome, pas le nombre d'evaluations qu'elle a produites.
        double sommePonderee = 0;
        double sommeCredits = 0;
        for (LigneUnite l : provisoire) {
            if (!l.estEvaluee()) continue;
            int credits = l.unite().getCredits() != null ? l.unite().getCredits() : 0;
            if (credits <= 0) continue;
            sommePonderee += l.moyenne() * credits;
            sommeCredits += credits;
        }
        Double moyenneSemestre = sommeCredits > 0 ? sommePonderee / sommeCredits : null;

        // Seconde passe : le statut de chaque unite, maintenant que la moyenne du semestre
        // est connue, et les credits reellement acquis.
        List<LigneUnite> finales = new ArrayList<>();
        int creditsAcquis = 0;
        int creditsPossibles = 0;
        for (LigneUnite l : provisoire) {
            int credits = l.unite().getCredits() != null ? l.unite().getCredits() : 0;
            creditsPossibles += credits;

            if (!l.estEvaluee() || moyenneSemestre == null) {
                finales.add(new LigneUnite(l.unite(), l.moyenne(), null, 0, l.elements()));
                continue;
            }
            var obtention = regime.statuer(etablissement, l.moyenne(), moyenneSemestre);
            int acquis = obtention == RegimeAcademiqueService.ModeObtention.NON_VALIDEE ? 0 : credits;
            creditsAcquis += acquis;
            finales.add(new LigneUnite(l.unite(), l.moyenne(), obtention, acquis, l.elements()));
        }

        String mention = moyenneSemestre != null ? regime.mention(etablissement, moyenneSemestre) : null;
        return new Releve(eleve, semestre, finales, moyenneSemestre, creditsAcquis, creditsPossibles, mention);
    }

    /**
     * Moyenne simple des notes de la matiere. Le coefficient porte par la note elle-meme sert
     * au regime scolaire ; ici c'est le coefficient de l'element constitutif qui pese, et le
     * cumuler deux fois fausserait l'unite.
     */
    private Double moyenneDansLaMatiere(List<Note> notes, Long matiereId) {
        if (matiereId == null) return null;
        List<Double> valeurs = notes.stream()
            .filter(n -> n.getMatiere() != null && matiereId.equals(n.getMatiere().getId()))
            .map(Note::getValeur)
            .filter(v -> v != null)
            .toList();
        if (valeurs.isEmpty()) return null;
        return valeurs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
