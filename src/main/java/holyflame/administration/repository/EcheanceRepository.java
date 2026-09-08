package holyflame.administration.repository;

import holyflame.administration.model.Echeance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcheanceRepository extends JpaRepository<Echeance, Long> {
    List<Echeance> findByEleveIdAndAnneeScolaireOrderByRangAsc(Long eleveId, String anneeScolaire);
    List<Echeance> findByEtablissementIdAndAnneeScolaireOrderByDatePrevueAsc(Long etablissementId, String anneeScolaire);
    void deleteByEleveIdAndAnneeScolaire(Long eleveId, String anneeScolaire);
}
