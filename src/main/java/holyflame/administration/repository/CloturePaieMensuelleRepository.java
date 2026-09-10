package holyflame.administration.repository;

import holyflame.administration.model.CloturePaieMensuelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CloturePaieMensuelleRepository extends JpaRepository<CloturePaieMensuelle, Long> {
    Optional<CloturePaieMensuelle> findByEtablissementIdAndMoisAndAnnee(Long etablissementId, int mois, int annee);
    List<CloturePaieMensuelle> findByEtablissementIdAndAnneeOrderByMoisAsc(Long etablissementId, int annee);
    boolean existsByEtablissementIdAndMoisAndAnnee(Long etablissementId, int mois, int annee);
}
