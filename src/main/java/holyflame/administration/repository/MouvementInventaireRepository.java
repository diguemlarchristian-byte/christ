package holyflame.administration.repository;

import holyflame.administration.model.MouvementInventaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MouvementInventaireRepository extends JpaRepository<MouvementInventaire, Long> {
    List<MouvementInventaire> findByArticleIdOrderByDateDesc(Long articleId);

    // Tout l'historique d'un etablissement en une requete : la page d'inventaire affiche
    // l'historique de chaque ligne, et une requete par ligne l'aurait rendue lente des la
    // premiere centaine d'articles. L'id departage les mouvements d'une meme journee.
    List<MouvementInventaire> findByArticleEtablissementIdOrderByDateDescIdDesc(Long etablissementId);
}
