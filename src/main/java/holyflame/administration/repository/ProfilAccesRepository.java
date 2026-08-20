package holyflame.administration.repository;

import holyflame.administration.model.ProfilAcces;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfilAccesRepository extends JpaRepository<ProfilAcces, Long> {
    List<ProfilAcces> findByEtablissementIdAndTypeOrderByNomAsc(Long etablissementId, String type);
}
