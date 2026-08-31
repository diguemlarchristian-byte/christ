package holyflame.administration.repository;

import holyflame.administration.model.ClotureMensuelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClotureMensuelleRepository extends JpaRepository<ClotureMensuelle, Long> {
    Optional<ClotureMensuelle> findByEtablissementIdAndMoisAndAnneeCivile(Long etablissementId, Integer mois, Integer anneeCivile);
    List<ClotureMensuelle> findByEtablissementIdOrderByAnneeCivileDescMoisDesc(Long etablissementId);
}
