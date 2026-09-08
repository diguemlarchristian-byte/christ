package holyflame.administration.repository;

import holyflame.administration.model.ElementConstitutif;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElementConstitutifRepository extends JpaRepository<ElementConstitutif, Long> {
    List<ElementConstitutif> findByUniteEnseignementIdOrderByIntituleAsc(Long uniteEnseignementId);
    List<ElementConstitutif> findByEnseignantIdOrderByIntituleAsc(Long enseignantId);
    List<ElementConstitutif> findByEtablissementIdOrderByIntituleAsc(Long etablissementId);
    long countByUniteEnseignementId(Long uniteEnseignementId);
    void deleteByUniteEnseignementId(Long uniteEnseignementId);
}
