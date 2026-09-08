package holyflame.administration.repository;

import holyflame.administration.model.UniteEnseignement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniteEnseignementRepository extends JpaRepository<UniteEnseignement, Long> {
    List<UniteEnseignement> findByParcoursIdOrderBySemestreAscIntituleAsc(Long parcoursId);
    List<UniteEnseignement> findByParcoursIdAndSemestreOrderByIntituleAsc(Long parcoursId, Integer semestre);
    List<UniteEnseignement> findByEtablissementIdOrderBySemestreAscIntituleAsc(Long etablissementId);
    Optional<UniteEnseignement> findByCodeIgnoreCaseAndEtablissementId(String code, Long etablissementId);
    long countByParcoursId(Long parcoursId);
}
