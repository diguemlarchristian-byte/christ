package holyflame.administration.repository;

import holyflame.administration.model.ArticleInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ArticleInventaireRepository extends JpaRepository<ArticleInventaire, Long> {
    List<ArticleInventaire> findByEtablissementIdOrderByCategorieAscNomAsc(Long etablissementId);
    long countByEtatAndEtablissementId(String etat, Long etablissementId);
    long countByEtablissementIdIsNull();

    // Articles historiques sans etablissement. Cette requete tournait a chaque affichage de la
    // page et rattachait ces articles a l'etablissement de qui l'ouvrait : dans une base a
    // plusieurs ecoles, le materiel de l'une atterrissait chez l'autre. Elle ne tourne plus
    // qu'au demarrage, et seulement s'il n'existe qu'un seul etablissement — sinon il n'y a
    // aucun moyen honnete de deviner le proprietaire, et mieux vaut laisser l'article de cote.
    @Modifying
    @Transactional
    @Query("UPDATE ArticleInventaire a SET a.etablissementId = :etabId WHERE a.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);

    // L'etat portait autrefois l'indisponibilite de tout un lot (EN_REPARATION, HORS_SERVICE).
    // Ces deux requetes reportent cette information sur les compteurs d'unites et ramenent
    // l'etat a la qualite du lot, sans quoi les anciens articles resteraient comptes en service.
    @Modifying
    @Transactional
    @Query("UPDATE ArticleInventaire a SET a.quantiteEnReparation = a.quantite, a.etat = 'USE' "
         + "WHERE a.etat = 'EN_REPARATION'")
    int migrerEtatEnReparation();

    @Modifying
    @Transactional
    @Query("UPDATE ArticleInventaire a SET a.quantiteHorsService = a.quantite, a.etat = 'USE' "
         + "WHERE a.etat = 'HORS_SERVICE'")
    int migrerEtatHorsService();
}
