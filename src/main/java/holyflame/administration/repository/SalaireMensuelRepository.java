package holyflame.administration.repository;

import holyflame.administration.model.SalaireMensuel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalaireMensuelRepository extends JpaRepository<SalaireMensuel, Long> {
    List<SalaireMensuel> findByAnneeOrderByMoisDescPersonnelNomAsc(int annee);
    List<SalaireMensuel> findByPersonnelIdOrderByAnneeDescMoisDesc(Long personnelId);
    List<SalaireMensuel> findByStatut(String statut);
    java.util.Optional<SalaireMensuel> findByPersonnelIdAndMoisAndAnnee(Long personnelId, int mois, int annee);

    // Rapprochement d'un bulletin papier et de son archive, par le code imprime dessus.
    java.util.Optional<SalaireMensuel> findByCodeVerification(String codeVerification);

    // Export des declarations sociales : le mois ou l'annee entiere, toujours ordonne comme
    // une declaration se lit — par periode, puis par nom.
    @Query("SELECT s FROM SalaireMensuel s WHERE s.personnel.etablissementId = :etabId "
         + "AND s.annee = :annee AND (:mois = 0 OR s.mois = :mois) "
         + "ORDER BY s.mois ASC, s.personnel.nom ASC, s.personnel.prenom ASC")
    List<SalaireMensuel> pourExport(@Param("etabId") Long etabId, @Param("annee") int annee, @Param("mois") int mois);

    @Query("SELECT s FROM SalaireMensuel s WHERE s.personnel.etablissementId = :etabId "
         + "ORDER BY s.annee DESC, s.mois DESC, s.personnel.nom ASC")
    List<SalaireMensuel> findByEtablissementId(@Param("etabId") Long etabId);
}
