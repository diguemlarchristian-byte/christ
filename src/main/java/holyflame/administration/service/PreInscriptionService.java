package holyflame.administration.service;

import holyflame.administration.model.DemandeInscription;
import holyflame.administration.repository.DemandeInscriptionRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recoit les demandes d'inscription deposees depuis le site public.
 *
 * Tout ce qui arrive ici vient d'un formulaire ouvert a tous : rien n'est authentifie, rien
 * n'est verifie. Ce service ne cherche donc pas a faire confiance — il borne. Il refuse ce
 * qui est inexploitable, coupe ce qui est trop long, ecarte les envois repetes, et laisse au
 * secretariat le soin de juger le reste.
 */
@Service
public class PreInscriptionService {

    /**
     * Ce que le service peut repondre a un envoi.
     *
     * Le formulaire etant public, l'echec doit s'expliquer a une famille — pas a un
     * developpeur. Chaque cas porte donc sa phrase.
     */
    public record Resultat(boolean accepte, String message, DemandeInscription demande) {

        static Resultat refus(String message) { return new Resultat(false, message, null); }

        static Resultat succes(DemandeInscription d) { return new Resultat(true, null, d); }
    }

    /** Sans O, 0, I ni 1 : la reference se lit au telephone. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int LONGUEUR_MAX_NOM = 120;
    private static final int LONGUEUR_MAX_MESSAGE = 1500;

    private final DemandeInscriptionRepository depot;
    private final SecureRandom hasard = new SecureRandom();

    public PreInscriptionService(DemandeInscriptionRepository depot) {
        this.depot = depot;
    }

    /**
     * Enregistre une demande, ou dit pourquoi elle ne l'est pas.
     *
     * @param piege champ invisible du formulaire. Une personne ne le voit pas et le laisse
     *              vide ; un robot remplit tout ce qu'il trouve. C'est une barriere modeste,
     *              qui arrete l'essentiel du bruit sans imposer d'epreuve aux familles.
     */
    public Resultat enregistrer(DemandeInscription demande, String anneeScolaire, String piege) {
        if (piege != null && !piege.isBlank()) {
            // On repond comme si tout allait bien : signaler le piege apprendrait a le
            // contourner. Rien n'est enregistre.
            return Resultat.refus(null);
        }

        String nom = propre(demande.getNomComplet(), LONGUEUR_MAX_NOM);
        if (nom == null || nom.length() < 3) {
            return Resultat.refus("Indiquez le nom complet de l'enfant.");
        }
        String parent = propre(demande.getParentNom(), LONGUEUR_MAX_NOM);
        if (parent == null || parent.length() < 3) {
            return Resultat.refus("Indiquez le nom du parent ou du tuteur.");
        }
        String telephone = propre(demande.getParentTelephone(), 30);
        if (telephone == null || telephone.replaceAll("\\D", "").length() < 6) {
            return Resultat.refus("Indiquez un numéro de téléphone où l'école peut vous joindre.");
        }

        // Une date de naissance dans le futur, ou vieille de plus d'un siecle, est une faute
        // de saisie. La refuser tout de suite evite une fiche a corriger plus tard.
        LocalDate naissance = demande.getDateNaissance();
        if (naissance != null && (naissance.isAfter(LocalDate.now())
                || naissance.isBefore(LocalDate.now().minusYears(100)))) {
            return Resultat.refus("La date de naissance saisie n'est pas possible. Vérifiez-la.");
        }

        if (depot.existsByEtablissementIdAndNomCompletIgnoreCaseAndParentTelephoneAndAnneeScolaire(
                demande.getEtablissementId(), nom, telephone, anneeScolaire)) {
            return Resultat.refus("Une demande pour cet enfant a déjà été reçue. "
                + "L'école vous rappellera — inutile de la renvoyer.");
        }

        demande.setNomComplet(nom);
        demande.setParentNom(parent);
        demande.setParentTelephone(telephone);
        demande.setParentEmail(propre(demande.getParentEmail(), 150));
        demande.setLieuNaissance(propre(demande.getLieuNaissance(), 120));
        demande.setNiveauDemande(propre(demande.getNiveauDemande(), 80));
        demande.setEcolePrecedente(propre(demande.getEcolePrecedente(), 150));
        demande.setMessage(propre(demande.getMessage(), LONGUEUR_MAX_MESSAGE));
        demande.setSexe(sexeValide(demande.getSexe()));

        demande.setAnneeScolaire(anneeScolaire);
        demande.setStatut(DemandeInscription.NOUVELLE);
        demande.setDateDemande(LocalDateTime.now());
        demande.setReference(referenceLibre());

        return Resultat.succes(depot.save(demande));
    }

    /** Le nombre de demandes qui attendent encore une decision. */
    public long enAttente(Long etablissementId) {
        return depot.countByEtablissementIdAndStatutIn(etablissementId,
            List.of(DemandeInscription.NOUVELLE, DemandeInscription.EN_COURS));
    }

    /**
     * Une reference unique, courte et dictable.
     *
     * La boucle ne peut pas tourner longtemps : quatre caracteres sur trente-deux donnent un
     * million de combinaisons par annee. La borne est la pour qu'un defaut de base ne se
     * transforme jamais en boucle infinie qui bloque le serveur de l'ecole.
     */
    private String referenceLibre() {
        String annee = String.valueOf(LocalDate.now().getYear());
        for (int essai = 0; essai < 50; essai++) {
            StringBuilder sb = new StringBuilder("PRE-").append(annee).append('-');
            for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
            String candidate = sb.toString();
            if (!depot.existsByReference(candidate)) return candidate;
        }
        // Repli : on ne renvoie jamais une reference qui pourrait deja exister.
        return "PRE-" + annee + "-" + System.nanoTime();
    }

    private String propre(String valeur, int maximum) {
        if (valeur == null) return null;
        String net = valeur.trim().replaceAll("\\s+", " ");
        if (net.isEmpty()) return null;
        return net.length() > maximum ? net.substring(0, maximum) : net;
    }

    private String sexeValide(String sexe) {
        if (sexe == null) return null;
        String s = sexe.trim().toUpperCase();
        return "M".equals(s) || "F".equals(s) ? s : null;
    }
}
