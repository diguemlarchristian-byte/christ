package holyflame.administration.repository;

import holyflame.administration.model.Parcours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParcoursRepository extends JpaRepository<Parcours, Long> {
    List<Parcours> findByEtablissementIdOrderByLibelleAsc(Long etablissementId);
    List<Parcours> findByEtablissementIdAndActifTrueOrderByLibelleAsc(Long etablissementId);
    Optional<Parcours> findByLibelleIgnoreCaseAndEtablissementId(String libelle, Long etablissementId);
}
