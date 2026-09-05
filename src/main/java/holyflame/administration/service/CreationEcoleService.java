package holyflame.administration.service;

import holyflame.administration.controller.InscriptionEcoleController.DonneesInscription;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creation d'un etablissement et de son premier compte administrateur.
 *
 * Cette logique etait ecrite en dur dans InscriptionEcoleController.finaliser(). Elle est
 * extraite ici pour que l'assistant detaille (quatre ecrans) et le demarrage rapide (un seul
 * ecran) produisent exactement le meme resultat : memes valeurs par defaut, meme generation du
 * code d'acces, memes classes creees. Sans cela, deux chemins de creation divergeraient au
 * premier changement de regle.
 */
@Service
public class CreationEcoleService {

    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlanComptableService planComptableService;

    /** Niveaux proposes a la creation : code technique -> {libelle affiche, cycle}. */
    public static final Map<String, String[]> NIVEAUX = new LinkedHashMap<>();
    static {
        NIVEAUX.put("MATERNELLE_PS", new String[]{"Petite Section", "Maternelle"});
        NIVEAUX.put("MATERNELLE_MS", new String[]{"Moyenne Section", "Maternelle"});
        NIVEAUX.put("MATERNELLE_GS", new String[]{"Grande Section", "Maternelle"});
        NIVEAUX.put("PRIMAIRE_CP1", new String[]{"CP1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CP2", new String[]{"CP2", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CE1", new String[]{"CE1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CE2", new String[]{"CE2", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CM1", new String[]{"CM1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CM2", new String[]{"CM2", "Primaire"});
        NIVEAUX.put("COLLEGE_6E", new String[]{"6ème", "Collège"});
        NIVEAUX.put("COLLEGE_5E", new String[]{"5ème", "Collège"});
        NIVEAUX.put("COLLEGE_4E", new String[]{"4ème", "Collège"});
        NIVEAUX.put("COLLEGE_3E", new String[]{"3ème", "Collège"});
        NIVEAUX.put("LYCEE_SECONDE", new String[]{"Seconde", "Lycée"});
        NIVEAUX.put("LYCEE_PREMIERE", new String[]{"Première", "Lycée"});
        NIVEAUX.put("LYCEE_TERMINALE", new String[]{"Terminale", "Lycée"});
        NIVEAUX.put("SUPERIEUR_L1", new String[]{"Licence 1", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_L2", new String[]{"Licence 2", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_L3", new String[]{"Licence 3", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_M1", new String[]{"Master 1", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_M2", new String[]{"Master 2", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_DOCTORAT", new String[]{"Doctorat", "Supérieur"});
    }

    /** Ce que l'ecran de confirmation doit afficher apres une creation reussie. */
    public record EcoleCreee(String nomEcole, String codeAcces, String adminEmail, String motDePasse,
                             int nbClassesCreees) {}

    /** Levee quand la creation ne peut pas aboutir pour une raison que l'utilisateur peut corriger. */
    public static class CreationRefusee extends RuntimeException {
        public CreationRefusee(String message) { super(message); }
    }

    public boolean emailDejaPris(String email) {
        return email != null && utilisateurRepository.findByEmail(email.trim()).isPresent();
    }

    /**
     * Cree l'etablissement, ses classes et son administrateur.
     *
     * @param donnees        parametres de l'etablissement ; les champs non renseignes prennent
     *                       leur valeur par defaut (notation numerique, seuil d'assiduite 75 %,
     *                       couleur #00236f, francais) — c'est ce qui permet au demarrage rapide
     *                       de ne demander que le strict necessaire.
     * @param adminNomComplet prenom et nom du premier administrateur.
     * @param adminEmail      son identifiant de connexion.
     * @param role            role a lui attribuer (ADMIN par defaut).
     */
    public EcoleCreee creer(DonneesInscription donnees, String adminNomComplet, String adminEmail, String role) {
        if (donnees == null || donnees.nom == null || donnees.nom.isBlank()) {
            throw new CreationRefusee("Le nom de l'ecole est obligatoire.");
        }
        if (adminNomComplet == null || adminNomComplet.isBlank() || adminEmail == null || adminEmail.isBlank()) {
            throw new CreationRefusee("Le nom et l'email de l'administrateur sont obligatoires.");
        }
        if (emailDejaPris(adminEmail)) {
            throw new CreationRefusee("Un compte existe deja avec cet email.");
        }

        int anneeCourante = LocalDate.now().getYear();

        Etablissement etab = new Etablissement();
        etab.setNom(donnees.nom.trim());
        etab.setAdresse(donnees.adresse);
        etab.setEmail(donnees.email);
        etab.setTelephone(donnees.telephone);
        etab.setTypeEtablissement(donnees.niveaux != null ? donnees.niveaux : donnees.categorie);
        etab.setContact(adminNomComplet.trim());
        etab.setAnneeScolaire(donnees.anneeScolaire != null
            ? donnees.anneeScolaire
            : anneeCourante + "-" + (anneeCourante + 1));
        etab.setStatut("ACTIF");
        etab.setDateCreation(LocalDate.now());
        String codeAcces = "HF-" + anneeCourante + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        etab.setCodeAcces(codeAcces);

        etab.setDateDebutSession(donnees.dateDebutSession);
        etab.setDateFinSession(donnees.dateFinSession);
        etab.setSystemeNotation(donnees.systemeNotation != null ? donnees.systemeNotation : "NUMERIQUE");
        etab.setSeuilAssiduite(donnees.seuilAssiduite != null ? donnees.seuilAssiduite : 75);
        etab.setAlerteAbsences(donnees.alerteAbsences);
        etab.setCalculRetard(donnees.calculRetard);
        etab.setStatutsIncompletAbandon(donnees.statutsIncompletAbandon);
        etab.setRangAutomatique(donnees.rangAutomatique);
        etab.setLogoPath(donnees.logoPath);
        etab.setCouleurPrimaire(donnees.couleurPrimaire != null ? donnees.couleurPrimaire : "#00236f");
        etab.setLangueSysteme(donnees.langueSysteme != null ? donnees.langueSysteme : "Francais");

        etablissementRepository.save(etab);
        planComptableService.seedSiVide(etab.getId());

        // Une classe "A" par niveau retenu ; sections et classes speciales s'ajoutent
        // ensuite depuis Gestion Academique.
        int nbClasses = 0;
        if (donnees.niveauxSelectionnes != null) {
            for (String code : donnees.niveauxSelectionnes) {
                String[] info = NIVEAUX.get(code);
                if (info == null) continue;
                Classe classe = new Classe();
                classe.setNom(info[0] + " A");
                classe.setNiveau(info[0]);
                classe.setAnneeScolaire(etab.getAnneeScolaire());
                classe.setEtablissementId(etab.getId());
                classeRepository.save(classe);
                nbClasses++;
            }
        }

        String[] parts = adminNomComplet.trim().split("\\s+", 2);
        String prenom = parts.length > 1 ? parts[0] : "";
        String nomFamille = parts.length > 1 ? parts[1] : parts[0];
        String motDePasseGenere = genererMotDePasse();

        Utilisateur admin = new Utilisateur();
        admin.setNom(nomFamille.toUpperCase());
        admin.setPrenom(prenom);
        admin.setEmail(adminEmail.trim());
        admin.setMotDePasse(passwordEncoder.encode(motDePasseGenere));
        admin.setRole(role != null ? role : "ADMIN");
        admin.setEtablissement(etab);
        utilisateurRepository.save(admin);

        return new EcoleCreee(etab.getNom(), codeAcces, admin.getEmail(), motDePasseGenere, nbClasses);
    }

    private String genererMotDePasse() {
        // ThreadLocalRandom plutot que SecureRandom : ce mot de passe est temporaire et
        // affiche immediatement a l'admin pour changement, pas besoin d'aleatoire cryptographique,
        // et SecureRandom peut se bloquer plusieurs secondes en attendant de l'entropie sur certains
        // conteneurs Linux (cause reelle d'un crash observe en production sur cette route).
        // L'alphabet exclut I, l, O, 0 et 1 : ce mot de passe est lu a l'ecran puis retape.
        String caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        var random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return sb.toString();
    }
}
