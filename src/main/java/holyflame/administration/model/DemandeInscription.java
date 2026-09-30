package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une demande d'inscription deposee par une famille depuis le site public de l'ecole.
 *
 * Ce n'est pas un eleve. C'est une intention, deposee par quelqu'un que l'ecole ne connait
 * pas encore, et dont rien n'est verifie : ni le nom, ni l'age, ni le telephone. La
 * confondre avec un dossier d'eleve reviendrait a laisser n'importe qui ecrire dans le
 * registre de l'etablissement.
 *
 * Elle vit donc a cote, dans sa propre table, jusqu'a ce qu'une personne du secretariat la
 * lise et decide. Convertir une demande n'inscrit personne : cela ouvre le parcours
 * d'inscription habituel avec les champs deja remplis. Les verifications de ce parcours —
 * classe obligatoire, piece d'identite, autorisations — restent les seules qui font foi.
 *
 * Sur une installation locale, le site public n'est joignable que depuis le reseau de
 * l'ecole : la famille remplit alors le formulaire sur place, sur une tablette a l'accueil,
 * au lieu de dicter ses informations a la secretaire. Sur l'hebergement en ligne, elle le
 * remplit de chez elle. Le traitement est le meme dans les deux cas.
 */
@Entity
@Table(name = "demandes_inscription", indexes = {
    @Index(name = "idx_demande_etab_statut", columnList = "etablissementId,statut")
})
public class DemandeInscription {

    /** Recue, personne ne l'a encore ouverte. */
    public static final String NOUVELLE = "NOUVELLE";
    /** Quelqu'un s'en occupe, ou attend une piece de la famille. */
    public static final String EN_COURS = "EN_COURS";
    /**
     * La commission a dit oui. L'eleve n'est pas inscrit pour autant.
     *
     * Entre l'admission et l'inscription, une famille change d'avis, part ailleurs, ou ne
     * reunit pas les frais. Confondre les deux ferait compter des eleves qui ne viendront
     * pas, et empecherait de rappeler la liste d'attente a temps.
     */
    public static final String ADMIS = "ADMIS";
    /** Admissible, mais apres les autres. Le rang est celui de la note. */
    public static final String LISTE_ATTENTE = "LISTE_ATTENTE";
    /** L'eleve a ete inscrit a partir de cette demande. */
    public static final String CONVERTIE = "CONVERTIE";
    /** L'ecole ne donne pas suite. Le motif est toujours renseigne. */
    public static final String REFUSEE = "REFUSEE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long etablissementId;

    /**
     * La reference que la famille emporte.
     *
     * Sans elle, un parent qui rappelle ne peut designer sa demande que par le nom de son
     * enfant — et deux familles portent souvent le meme. Elle est donnee a l'ecran apres
     * l'envoi, et redite dans le SMS de confirmation quand l'ecole en envoie un.
     */
    @Column(unique = true, nullable = false, length = 20)
    private String reference;

    // ── L'enfant ────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String nomComplet;

    private LocalDate dateNaissance;
    private String lieuNaissance;

    /** M ou F. Laisse libre : une famille peut ne pas vouloir le dire a ce stade. */
    private String sexe;

    /** Le niveau demande, tel que l'ecole l'a nomme dans ses classes. */
    private String niveauDemande;

    /** Etablissement precedent, s'il y en a un. */
    private String ecolePrecedente;

    // ── Qui depose la demande ───────────────────────────────────────────
    @Column(nullable = false)
    private String parentNom;

    @Column(nullable = false)
    private String parentTelephone;

    private String parentEmail;

    /** Ce que la famille a voulu ajouter. */
    @Column(length = 1500)
    private String message;

    // ── Suivi par l'ecole ───────────────────────────────────────────────
    @Column(nullable = false)
    private String statut = NOUVELLE;

    @Column(nullable = false)
    private String anneeScolaire;

    @Column(nullable = false)
    private LocalDateTime dateDemande = LocalDateTime.now();

    private LocalDateTime dateTraitement;

    /** Qui a traite la demande, pour que le directeur sache a qui en parler. */
    private String traiteePar;

    /**
     * Motif du refus, ou note interne.
     *
     * Jamais montre a la famille : c'est une note de travail entre collegues. Un refus se
     * dit de vive voix ou par courrier, avec les mots que l'ecole choisit.
     */
    @Column(length = 1000)
    private String noteInterne;

    /** L'eleve cree a partir de cette demande, une fois convertie. */
    private Long eleveId;

    // ── Etude du dossier ────────────────────────────────────────────────

    /**
     * La note du dossier sur vingt, ponderee par les poids des criteres.
     *
     * Recalculee a chaque saisie et conservee ici plutot que refaite a l'affichage : c'est
     * elle qui ordonne la liste d'attente, et un classement qui changerait entre deux
     * consultations ne serait pas defendable devant une famille.
     */
    private Double noteDossier;

    /** Ce que la commission a retenu du dossier. Peut etre communique a la famille. */
    @Column(length = 1000)
    private String appreciation;

    /** Qui a pris la decision, et quand — un jury rend des comptes. */
    private String decidePar;

    private LocalDateTime dateDecision;

    public Double getNoteDossier() { return noteDossier; }
    public void setNoteDossier(Double noteDossier) { this.noteDossier = noteDossier; }

    public String getAppreciation() { return appreciation; }
    public void setAppreciation(String appreciation) { this.appreciation = appreciation; }

    public String getDecidePar() { return decidePar; }
    public void setDecidePar(String decidePar) { this.decidePar = decidePar; }

    public LocalDateTime getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDateTime dateDecision) { this.dateDecision = dateDecision; }

    public DemandeInscription() {
    }

    /** Vrai tant que personne n'a tranche : la demande attend une decision. */
    public boolean estEnAttente() {
        return NOUVELLE.equals(statut) || EN_COURS.equals(statut);
    }

    /** Vrai quand la commission a dit oui, que l'inscription ait suivi ou non. */
    public boolean estAdmise() {
        return ADMIS.equals(statut) || CONVERTIE.equals(statut);
    }

    /**
     * Vrai quand la place reste a prendre.
     *
     * C'est la liste que le secretariat rappelle en septembre, quand des admis ne se sont
     * pas presentes. Une demande deja convertie n'y figure pas : elle a sa place.
     */
    public boolean attendSaPlace() {
        return ADMIS.equals(statut) || LISTE_ATTENTE.equals(statut);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public String getNiveauDemande() { return niveauDemande; }
    public void setNiveauDemande(String niveauDemande) { this.niveauDemande = niveauDemande; }

    public String getEcolePrecedente() { return ecolePrecedente; }
    public void setEcolePrecedente(String ecolePrecedente) { this.ecolePrecedente = ecolePrecedente; }

    public String getParentNom() { return parentNom; }
    public void setParentNom(String parentNom) { this.parentNom = parentNom; }

    public String getParentTelephone() { return parentTelephone; }
    public void setParentTelephone(String parentTelephone) { this.parentTelephone = parentTelephone; }

    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }

    public LocalDateTime getDateDemande() { return dateDemande; }
    public void setDateDemande(LocalDateTime dateDemande) { this.dateDemande = dateDemande; }

    public LocalDateTime getDateTraitement() { return dateTraitement; }
    public void setDateTraitement(LocalDateTime dateTraitement) { this.dateTraitement = dateTraitement; }

    public String getTraiteePar() { return traiteePar; }
    public void setTraiteePar(String traiteePar) { this.traiteePar = traiteePar; }

    public String getNoteInterne() { return noteInterne; }
    public void setNoteInterne(String noteInterne) { this.noteInterne = noteInterne; }

    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }
}
