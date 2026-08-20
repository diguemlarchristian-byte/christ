package holyflame.administration.repository;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByEleveOrderByDateEvaluationDesc(Eleve eleve);
    List<Note> findByTrimestreOrderByDateEvaluationDesc(Integer trimestre);
    List<Note> findByEleveAndTrimestreOrderByMatiereNomAsc(Eleve eleve, Integer trimestre);
    List<Note> findByEleveAndTrimestreAndAnneeScolaireOrderByMatiereNomAsc(Eleve eleve, Integer trimestre, String anneeScolaire);
    List<Note> findByEleveAndAnneeScolaire(Eleve eleve, String anneeScolaire);

    @Query("SELECT n FROM Note n JOIN FETCH n.eleve e JOIN FETCH e.classe WHERE n.anneeScolaire IS NULL")
    List<Note> findByAnneeScolaireIsNullWithEleveEtClasse();

    // Nettoyage : notes orphelines (annee jamais figee, ou eleve sans classe) — l'annee
    // et la classe sont desormais obligatoires a la saisie, ces notes ne peuvent donc
    // provenir que de donnees anciennes a purger.
    List<Note> findByAnneeScolaireIsNull();

    @Query("SELECT n FROM Note n WHERE n.eleve.classe IS NULL")
    List<Note> findByEleveClasseIsNull();

    List<Note> findAllByOrderByDateEvaluationDesc();

    // Pour le suivi : compter les notes saisies par matière + classe + trimestre
    @Query("SELECT COUNT(n) FROM Note n WHERE n.matiere.id = :matiereId AND n.eleve.classe.id = :classeId AND n.trimestre = :trimestre")
    long countByMatiereClasseTrimestre(@Param("matiereId") Long matiereId,
                                       @Param("classeId") Long classeId,
                                       @Param("trimestre") Integer trimestre);

    // Dernière saisie pour une combinaison matière + classe
    @Query("SELECT n FROM Note n WHERE n.matiere.id = :matiereId AND n.eleve.classe.id = :classeId ORDER BY n.saisieAt DESC")
    List<Note> findTopByMatiereAndClasse(@Param("matiereId") Long matiereId,
                                         @Param("classeId") Long classeId);

    // Notes saisies par un utilisateur
    List<Note> findBySaisieParIdOrderBySaisieAtDesc(Long saisieParId);

    @Query("SELECT n FROM Note n WHERE n.eleve.etablissementId = :etabId ORDER BY n.dateEvaluation DESC")
    List<Note> findByEtablissementId(@Param("etabId") Long etabId);

    @Query("SELECT n FROM Note n WHERE n.eleve.etablissementId = :etabId AND n.type = :type ORDER BY n.dateEvaluation DESC")
    List<Note> findByEtablissementIdAndType(@Param("etabId") Long etabId, @Param("type") String type);

    @Query("SELECT COUNT(n) FROM Note n WHERE n.matiere.id = :id")
    long countByMatiereId(@Param("id") Long matiereId);

    @Modifying
    @Query("DELETE FROM Note n WHERE n.matiere.id = :id")
    void deleteByMatiereId(@Param("id") Long matiereId);

    @Modifying
    @Query("DELETE FROM Note n WHERE n.eleve.id = :id")
    void deleteByEleveId(@Param("id") Long eleveId);

    // Nombre d'élèves distincts ayant une note pour une matière+classe+type+trimestre donnés
    @Query("SELECT COUNT(DISTINCT n.eleve.id) FROM Note n " +
           "WHERE n.matiere.id = :matiereId AND n.eleve.classe.id = :classeId " +
           "AND n.type = :type AND n.trimestre = :trimestre")
    long countElevesByMatiereClasseTypeTrimestre(@Param("matiereId") Long matiereId,
                                                 @Param("classeId") Long classeId,
                                                 @Param("type") String type,
                                                 @Param("trimestre") Integer trimestre);

    // Matieres pour lesquelles au moins une note existe deja, pour une classe+annee donnee — sert
    // au dashboard de sante de fin d'annee (assistant de cloture) pour reperer les matieres non
    // encore couvertes sans avoir a interroger chaque combinaison matiere/trimestre/type une a une.
    @Query("SELECT DISTINCT n.matiere.id FROM Note n WHERE n.eleve.classe.id = :classeId AND n.anneeScolaire = :anneeScolaire")
    List<Long> findDistinctMatiereIdByClasseIdAndAnneeScolaire(@Param("classeId") Long classeId,
                                                                @Param("anneeScolaire") String anneeScolaire);

    // Dates distinctes des evaluations deja saisies pour une matiere+classe+trimestre+annee et un
    // ensemble de types donnes — sert a compter le nombre d'evaluations (devoirs ou examens)
    // deja enregistrees, independamment du nombre d'eleves notes pour chacune.
    @Query("SELECT DISTINCT n.dateEvaluation FROM Note n " +
           "WHERE n.matiere.id = :matiereId AND n.eleve.classe.id = :classeId " +
           "AND n.trimestre = :trimestre AND n.anneeScolaire = :anneeScolaire AND n.type IN :types")
    List<LocalDate> findDatesEvaluationsParMatiereClasseTrimestre(@Param("matiereId") Long matiereId,
                                                 @Param("classeId") Long classeId,
                                                 @Param("trimestre") Integer trimestre,
                                                 @Param("anneeScolaire") String anneeScolaire,
                                                 @Param("types") List<String> types);
}
