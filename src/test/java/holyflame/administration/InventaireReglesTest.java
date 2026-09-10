package holyflame.administration;

import holyflame.administration.model.ArticleInventaire;
import holyflame.administration.service.InventaireRegles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'inventaire portait un seul etat pour tout un lot : trois chaises cassees sur quarante
 * n'etaient pas representables, et un depart en reparation basculait les quarante d'un coup.
 * Ces tests fixent le comportement des trois compteurs — en service, en reparation, hors
 * service — et surtout le refus des mouvements impossibles : un inventaire qui rogne une
 * quantite en silence ment a celui qui le presente en inspection.
 */
class InventaireReglesTest {

    private ArticleInventaire lot(int quantite) {
        ArticleInventaire a = new ArticleInventaire();
        a.setNom("Tables-bancs"); a.setCategorie("MOBILIER"); a.setEtat("BON_ETAT");
        a.setQuantite(quantite); a.setValeurUnitaire(25_000.0);
        return a;
    }

    @Nested
    @DisplayName("Le quotidien d'un lot de quarante tables-bancs")
    class Quotidien {

        @Test
        void troisTablesPartentEnReparationSansToucherAuxTrenteSept() {
            ArticleInventaire a = lot(40);

            InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 3);

            assertEquals(40, a.getQuantite(), "l'ecole possede toujours quarante tables");
            assertEquals(3, a.getQuantiteEnReparation());
            assertEquals(37, a.getQuantiteEnService(), "les autres restent utilisables");
        }

        @Test
        void deuxRevienentReparees_uneEstDeclareeIrreparable() {
            ArticleInventaire a = lot(40);
            InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 3);

            InventaireRegles.appliquer(a, InventaireRegles.RETOUR_REPARATION, 2);
            InventaireRegles.appliquer(a, InventaireRegles.HORS_SERVICE, 1);

            assertEquals(0, a.getQuantiteEnReparation(), "l'atelier a rendu son verdict sur les trois");
            assertEquals(1, a.getQuantiteHorsService());
            assertEquals(39, a.getQuantiteEnService());
            assertEquals(40, a.getQuantite(), "la table irreparable est encore la, au fond de la cour");
        }

        @Test
        void laValeurNeCompteQueCeQuiSert() {
            ArticleInventaire a = lot(40);
            InventaireRegles.appliquer(a, InventaireRegles.HORS_SERVICE, 4);

            assertEquals(36 * 25_000.0, a.getValeurEnService(), 0.01,
                "quatre tables cassees ne valent plus rien a l'inventaire");
        }

        @Test
        void laReformeSortDeLInventaireEnCommencantParLeCasse() {
            ArticleInventaire a = lot(40);
            InventaireRegles.appliquer(a, InventaireRegles.HORS_SERVICE, 4);

            InventaireRegles.appliquer(a, InventaireRegles.SORTIE, 4);

            assertEquals(36, a.getQuantite());
            assertEquals(0, a.getQuantiteHorsService(), "les quatre epaves ont quitte l'ecole");
            assertEquals(36, a.getQuantiteEnService(), "aucune table en service n'a ete cedee");
        }

        @Test
        void uneEntreeAugmenteLeLot() {
            ArticleInventaire a = lot(40);
            InventaireRegles.appliquer(a, InventaireRegles.ENTREE, 10);

            assertEquals(50, a.getQuantite());
            assertEquals(50, a.getQuantiteEnService());
        }
    }

    @Nested
    @DisplayName("Ce que l'inventaire refuse, plutot que de le rogner en silence")
    class Refus {

        @Test
        void onNEnvoiePasEnReparationPlusDUnitesQuOnEnADeDisponibles() {
            ArticleInventaire a = lot(5);
            InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 5);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 1));

            assertTrue(e.getMessage().contains("0 unite(s)"), "le message dit ce qui reste : " + e.getMessage());
            assertEquals(5, a.getQuantiteEnReparation(), "le refus ne doit rien avoir modifie");
        }

        @Test
        void onNeFaitPasRevenirPlusDUnitesQuIlNEnEstParti() {
            ArticleInventaire a = lot(10);
            InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 2);

            assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, InventaireRegles.RETOUR_REPARATION, 3));

            assertEquals(2, a.getQuantiteEnReparation());
        }

        @Test
        void onNeCedePasDuMaterielQuiEstChezLeReparateur() {
            ArticleInventaire a = lot(10);
            InventaireRegles.appliquer(a, InventaireRegles.REPARATION, 4);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, InventaireRegles.SORTIE, 7));

            assertTrue(e.getMessage().contains("reparation"), "le message explique pourquoi : " + e.getMessage());
            assertEquals(10, a.getQuantite());
        }

        @Test
        void uneQuantiteNulleOuNegativeNEstPasUnMouvement() {
            ArticleInventaire a = lot(10);

            assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, InventaireRegles.ENTREE, 0));
            assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, InventaireRegles.SORTIE, -3));
        }

        @Test
        void unTypeInconnuEstRefuse() {
            ArticleInventaire a = lot(10);

            assertThrows(IllegalArgumentException.class,
                () -> InventaireRegles.appliquer(a, "PRET", 1));
        }
    }

    @Test
    void lesCodesTechniquesOntTousUnLibelleFrancais() {
        assertEquals("Materiel de bureau", InventaireRegles.libelleCategorie("MATERIEL_BUREAU"));
        assertEquals("Bon etat", InventaireRegles.libelleEtat("BON_ETAT"));
        assertEquals("Retour de reparation", InventaireRegles.libelleMouvement("RETOUR_REPARATION"));
        assertEquals("—", InventaireRegles.libelleCategorie(null), "une categorie absente ne casse pas la ligne");
        assertEquals("ANCIEN_CODE", InventaireRegles.libelleCategorie("ANCIEN_CODE"),
            "un code retire du menu s'affiche tel quel plutot que de disparaitre");
    }
}
