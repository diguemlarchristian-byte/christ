package holyflame.administration.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import holyflame.administration.model.Etablissement;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * Produit le guide de prise en main remis a une ecole qui demarre.
 *
 * Ce document s'adresse a quelqu'un qui ouvre le logiciel pour la premiere fois — souvent le
 * jour de la rentree, souvent sans personne pour l'accompagner. Il n'enumere donc ni les
 * corrections apportees, ni les precautions du developpeur, ni ce qu'il faudrait ameliorer :
 * un utilisateur n'a que faire de savoir ce qui a ete repare, et une liste de mises en garde
 * en tete de guide fait douter de l'outil avant meme de l'avoir ouvert.
 *
 * Il dit ce que le logiciel fait, a qui il sert, et par quoi commencer.
 */
@Service
public class GuideInitiationPdfService {

    public byte[] genererPdf(Etablissement etablissement) {
        String nom = etablissement != null ? etablissement.getNom() : null;
        String annee = etablissement != null ? etablissement.getAnneeScolaire() : null;

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html(nom, annee), null);
            builder.toStream(sortie);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Le guide de prise en main n'a pas pu etre produit.", e);
        }
        return sortie.toByteArray();
    }

    /** Le HTML source, expose pour que le contenu du livrable soit verifiable sans lire un PDF. */
    public String html(String nomEtablissement, String annee) {
        // Le repli sur une formule neutre vit ici, avec le rendu : un guide edite avant que
        // l'ecole soit renseignee doit rester lisible, pas afficher un blanc au milieu du titre.
        String nom = nomEtablissement == null || nomEtablissement.isBlank()
            ? "votre etablissement" : nomEtablissement;
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/><style>
            @page { size: A4; margin: 18mm 16mm; }
            body { font-family: Helvetica, Arial, sans-serif; font-size: 10pt; color: #1a1d2b; line-height: 1.5; }
            h1 { font-size: 24pt; color: #00236f; margin: 0 0 2pt; }
            h2 { font-size: 13pt; color: #00236f; margin: 20pt 0 6pt; border-bottom: 1px solid #00236f; padding-bottom: 3pt; }
            h3 { font-size: 10.5pt; margin: 13pt 0 3pt; color: #1a1d2b; }
            .sous { color: #5b6478; font-size: 11pt; margin: 0 0 4pt; }
            .accroche { background: #eef1fa; border-left: 3px solid #00236f; padding: 10pt 12pt; margin: 14pt 0 4pt; font-size: 10.5pt; }
            .repere { background: #f4f6fb; padding: 8pt 10pt; margin: 8pt 0; font-size: 9.5pt; }
            table { width: 100%%; border-collapse: collapse; margin: 6pt 0 12pt; font-size: 9pt; }
            th { text-align: left; background: #00236f; color: #fff; padding: 5pt 6pt; font-size: 8.5pt; }
            td { padding: 4pt 6pt; border-bottom: 0.5pt solid #d9dde8; vertical-align: top; }
            td.role { font-weight: bold; white-space: nowrap; width: 26%%; }
            ol, ul { margin: 4pt 0 10pt 16pt; padding: 0; }
            li { margin-bottom: 5pt; }
            .cle { background: #eef1fa; padding: 1pt 4pt; font-family: monospace; font-size: 9pt; }
            .etape { font-weight: bold; color: #00236f; }
            .pied { margin-top: 18pt; padding-top: 7pt; border-top: 0.5pt solid #d9dde8; color: #5b6478; font-size: 8.5pt; }
            .saut { page-break-before: always; }
            </style></head><body>

            <h1>EduSystem Pro</h1>
            <p class="sous">Guide de prise en main — %s%s</p>

            <div class="accroche">
              <b>Toute l'ecole dans un seul outil.</b> Les inscriptions, les notes et les bulletins,
              la caisse et la comptabilite, le personnel et la paie, les absences, l'infirmerie,
              le materiel — et un portail ou les parents suivent leur enfant. Chacun ouvre sa
              propre page et n'y voit que son travail.
            </div>
            <p>EduSystem Pro fonctionne <b>sur le reseau de l'etablissement, sans connexion
            Internet</b> : le logiciel, ses polices et ses documents sont servis par votre propre
            machine. Une coupure de reseau n'arrete ni une inscription, ni un encaissement, ni
            l'edition d'un bulletin.</p>

            <h2>1. Ce que le logiciel fait pour vous</h2>
            <table>
              <tr><th>Au quotidien</th><th>Ce que cela vous evite</th></tr>
              <tr><td>Inscrire un eleve et editer sa fiche</td><td>Recopier les memes informations dans trois cahiers</td></tr>
              <tr><td>Encaisser et delivrer un recu numerote</td><td>Chercher quel recu a ete donne a qui</td></tr>
              <tr><td>Saisir les notes, editer les bulletins</td><td>Calculer les moyennes et les rangs a la main</td></tr>
              <tr><td>Suivre les impayes famille par famille</td><td>Reprendre tout le registre avant chaque relance</td></tr>
              <tr><td>Etablir la paie et ses bulletins</td><td>Refaire les memes calculs chaque fin de mois</td></tr>
              <tr><td>Editer grand livre et balance</td><td>Reconstituer les comptes en fin d'annee</td></tr>
            </table>

            <h2>2. Qui fait quoi</h2>
            <p>Chaque personne recoit un compte avec une <b>fonction</b>. La fonction decide de ce
            qu'elle voit en ouvrant le logiciel : personne ne se perd dans des ecrans qui ne le
            concernent pas, et les informations sensibles restent chez ceux qui en ont l'usage.</p>
            <table>
              <tr><th>Fonction</th><th>Ouvre sur</th><th>S'occupe de</th></tr>
              <tr><td class="role">Administrateur</td><td>Tableau de bord</td><td>Comptes, parametres, vue d'ensemble</td></tr>
              <tr><td class="role">Directeur</td><td>Tableau de bord</td><td>Suivi pedagogique, personnel, resultats</td></tr>
              <tr><td class="role">Secretaire</td><td>Secretariat</td><td>Inscriptions, dossiers, absences, materiel</td></tr>
              <tr><td class="role">Enseignant</td><td>Tableau enseignant</td><td>Ses classes, ses notes, ses bulletins</td></tr>
              <tr><td class="role">Tresorier / Comptable</td><td>Finances</td><td>Caisse, depenses, frais, documents comptables</td></tr>
              <tr><td class="role">Surveillant</td><td>Surveillance</td><td>Presences, retards, incidents</td></tr>
              <tr><td class="role">Infirmier</td><td>Infirmerie</td><td>Soins, stock de la pharmacie</td></tr>
              <tr><td class="role">Parent</td><td>Portail parent</td><td>Notes, absences et paiements de son enfant</td></tr>
            </table>
            <div class="repere">
              Une fonction n'est pas figee. L'administrateur ajuste, compte par compte, ce que
              chacun peut ouvrir — c'est ainsi qu'une meme ecole confie la caisse au tresorier
              dans un cas, a la comptable dans l'autre.
            </div>

            <div class="saut"></div>
            <h2>3. Vos premiers pas</h2>
            <p>Comptez une matinee pour ces cinq etapes. Elles se font une seule fois, et tout le
            reste de l'annee en decoule.</p>
            <ol>
              <li><span class="etape">Se connecter.</span> Ouvrez l'adresse du logiciel dans le
                  navigateur, saisissez l'adresse email et le mot de passe qui vous ont ete remis.
                  Vous arrivez directement sur la page de votre fonction.</li>
              <li><span class="etape">Renseigner l'etablissement.</span> Ecran
                  <span class="cle">Parametres</span> : nom, adresse, telephone, logo, annee
                  scolaire. Ces informations apparaitront sur les recus et les bulletins.</li>
              <li><span class="etape">Creer les classes, puis les comptes.</span> Les classes
                  d'abord — un eleve s'inscrit dans une classe. Les comptes du personnel ensuite,
                  depuis <span class="cle">Personnel</span>, en donnant a chacun sa fonction.</li>
              <li><span class="etape">Definir les frais de scolarite.</span> Ecran
                  <span class="cle">Frais de scolarite</span>. Un frais marque obligatoire alimente
                  le tableau des impayes ; un frais facultatif, comme la cantine, n'y figure pas.</li>
              <li><span class="etape">Inscrire les eleves.</span> Un par un, ou par import depuis
                  un fichier Excel si vous avez deja une liste.</li>
            </ol>
            <div class="repere">
              <b>L'ordre compte.</b> Les classes avant les eleves, les frais avant les
              encaissements. Le logiciel vous laissera faire autrement, mais vous devrez revenir
              en arriere.
            </div>

            <h2>4. Le rythme d'une annee</h2>
            <h3>Chaque jour</h3>
            <p>Les absences sont saisies le matin. Les encaissements se font au fil de l'eau : on
            choisit l'eleve, le montant, le mode de reglement, et le recu part avec son numero.</p>
            <h3>Chaque mois</h3>
            <p>La paie est etablie puis payee ; chaque bulletin paye est archive automatiquement.
            La caisse se compte, et le compte physique se compare au solde du logiciel.</p>
            <h3>A chaque trimestre</h3>
            <p>Les enseignants saisissent leurs notes, les bulletins sont edites et imprimes en
            une fois pour toute une classe.</p>
            <h3>En fin d'annee</h3>
            <p>Le grand livre et la balance sont edites pour l'expert-comptable, les eleves
            passent en classe superieure, et l'annee se cloture.</p>

            <h2>5. Trois habitudes qui font gagner du temps</h2>
            <ul>
              <li><b>Laissez le logiciel numeroter.</b> Recus et matricules se suivent tout seuls.
                  Un numero saisi a la main finit toujours par etre donne deux fois.</li>
              <li><b>Rattachez chaque depense a son poste comptable</b> et joignez la piece.
                  En fin d'annee, le grand livre sera deja pret.</li>
              <li><b>Exportez et conservez une copie hors du logiciel.</b> La paie du mois, la
                  liste des eleves, l'inventaire : un fichier range ailleurs, au meme rythme que
                  vos sauvegardes.</li>
            </ul>

            <h2>6. Si vous hesitez</h2>
            <p>Chaque ecran indique ce qu'il attend, et refuse une operation impossible en disant
            pourquoi plutot que de la corriger en silence. Un chiffre qui vous surprend s'explique
            presque toujours en ouvrant le detail de la ligne : le grand livre montre l'ecriture,
            le suivi des familles montre les versements, la fiche d'un article montre ses
            mouvements.</p>
            <p>Le <b>journal d'activite</b> conserve la trace de ce qui a ete fait, et par qui.
            C'est la qu'on regarde quand une donnee a change sans qu'on sache comment.</p>

            <div class="pied">
              EduSystem Pro — guide de prise en main de %s. Ce document presente l'outil et son
              demarrage ; le detail de chaque ecran figure dans le tutoriel d'utilisation.
            </div>
            </body></html>
            """.formatted(
                echapper(nom),
                annee == null || annee.isBlank() ? "" : ", annee " + echapper(annee),
                echapper(nom));
    }

    private static String echapper(String texte) {
        if (texte == null) return "";
        return texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
