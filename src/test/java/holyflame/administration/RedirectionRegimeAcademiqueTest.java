package holyflame.administration;

import holyflame.administration.service.Fonctionnalites;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les deux ecrans de l'organisation pedagogique se renvoient l'un a l'autre selon le regime de
 * l'etablissement : une ecole arrivee par l'adresse sur la maquette LMD retombe sur
 * /gestion-academique, et une universite arrivee sur /gestion-academique retombe sur la
 * maquette. La politesse est bonne — a condition que la porte d'arrivee soit ouverte.
 *
 * Elle ne l'etait pas. Un coordonnateur connecte dans un etablissement scolaire demandait
 * /academique-universite, que ses droits lui accordent, et recevait un 403 — mais sur
 * /gestion-academique, ou le renvoi l'avait conduit et dont il n'a pas la fonctionnalite.
 * L'erreur designait donc une adresse qu'il n'avait jamais demandee, et tout portait a croire
 * que la maquette lui etait fermee alors qu'elle ne l'a jamais ete.
 *
 * Ce genre de defaut ne se lit ni dans SecurityConfig, ou la regle est juste, ni dans le
 * registre, ou le droit est bien accorde : il nait de la rencontre des deux. Ces tests tiennent
 * les deux bouts — le fait que les deux droits se portent separement, et le controle qui empeche
 * un renvoi de courtoisie de finir en refus.
 */
@DisplayName("Renvoi entre les deux ecrans de l'organisation pedagogique")
class RedirectionRegimeAcademiqueTest {

    private static final Path CONTROLEURS =
        Paths.get("src", "main", "java", "holyflame", "administration", "controller");

    private String source(String fichier) throws IOException {
        return Files.readString(CONTROLEURS.resolve(fichier), StandardCharsets.UTF_8);
    }

    /**
     * La condition qui precede ce renvoi, pour verifier ce qu'elle exige avant de l'effectuer.
     * On remonte depuis le {@code return} jusqu'au {@code if} qui le gouverne.
     */
    private String conditionDuRenvoi(String fichier, String adresse) throws IOException {
        String source = source(fichier);
        String renvoi = "return \"redirect:" + adresse + "\";";
        int ligne = source.indexOf(renvoi);
        assertTrue(ligne > 0, fichier + " ne renvoie plus vers " + adresse
            + " : si le renvoi a ete retire, ce test n'a plus d'objet et doit l'etre aussi");
        int garde = source.lastIndexOf("if (", ligne);
        assertTrue(garde > 0, "le renvoi vers " + adresse + " n'est gouverne par aucune condition");
        return source.substring(garde, ligne);
    }

    @Test
    void tenirLaMaquetteEtTenirLOrganisationAcademiqueSontDeuxDroitsSepares() {
        Set<String> coordonnateur = Fonctionnalites.defautsPourRole("COORDONNATEUR");
        Set<String> directeur = Fonctionnalites.defautsPourRole("DIRECTEUR");

        // C'est cet ecart qui rend un renvoi inconditionnel dangereux : chacun des deux postes
        // detient exactement l'un des deux ecrans. Si un jour les deux droits allaient toujours
        // ensemble, les controles verifies plus bas deviendraient inutiles — mais tant que ce
        // test passe, ils protegent un cas reel.
        assertTrue(coordonnateur.contains(Fonctionnalites.UNIV_MAQUETTE), "il tient la maquette");
        assertFalse(coordonnateur.contains(Fonctionnalites.ACADEMIQUE),
            "mais pas l'organisation academique de l'etablissement");

        assertTrue(directeur.contains(Fonctionnalites.ACADEMIQUE), "le directeur l'organise");
        assertFalse(directeur.contains(Fonctionnalites.UNIV_MAQUETTE),
            "mais ne tient pas la maquette de chaque parcours");
    }

    @Test
    void laMaquetteNeRenvoieVersLOrganisationAcademiqueQueSiElleEstOuverte() throws IOException {
        String condition = conditionDuRenvoi("AcademiqueUniversiteController.java", "/gestion-academique");

        // Sans cette verification, un coordonnateur demandant la maquette dans un etablissement
        // scolaire recoit un refus portant sur une autre adresse que la sienne.
        assertTrue(condition.contains("Fonctionnalites.ACADEMIQUE"),
            "le renvoi vers /gestion-academique doit d'abord s'assurer que la personne y a acces");
    }

    @Test
    void lOrganisationAcademiqueNeRenvoieVersLaMaquetteQueSiElleEstOuverte() throws IOException {
        String condition = conditionDuRenvoi("GestionAcademiqueController.java", "/academique-universite");

        // Cote universite, c'est le directeur qui serait refoule — et /gestion-academique est
        // la seule porte vers les matieres, dont dependent toutes les notes, LMD comprises.
        assertTrue(condition.contains("Fonctionnalites.UNIV_MAQUETTE"),
            "le renvoi vers /academique-universite doit d'abord s'assurer que la personne y a acces");
    }
}
