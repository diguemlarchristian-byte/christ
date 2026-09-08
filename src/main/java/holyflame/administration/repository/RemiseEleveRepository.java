package holyflame.administration.repository;

import holyflame.administration.model.RemiseEleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemiseEleveRepository extends JpaRepository<RemiseEleve, Long> {
    List<RemiseEleve> findByEtablissementIdAndAnneeScolaire(Long etablissementId, String anneeScolaire);
    List<RemiseEleve> findByEleveIdAndAnneeScolaire(Long eleveId, String anneeScolaire);
    List<RemiseEleve> findByEtablissementIdOrderByDateAccordDesc(Long etablissementId);
    void deleteByEleveId(Long eleveId);
}
