package holyflame.administration.repository;

import holyflame.administration.model.CritereAdmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CritereAdmissionRepository extends JpaRepository<CritereAdmission, Long> {

    /** La grille telle qu'elle est notee aujourd'hui. */
    List<CritereAdmission> findByEtablissementIdAndActifTrueOrderByOrdreAsc(Long etablissementId);

    /** Tout, y compris les criteres retires : un dossier ancien porte encore leurs notes. */
    List<CritereAdmission> findByEtablissementIdOrderByOrdreAsc(Long etablissementId);
}
