package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Reglages du site vitrine public d'un etablissement (un seul par etablissement). Reutilise
 * volontairement les champs deja presents sur Etablissement (nom, logoPath, couleurPrimaire,
 * adresse, telephone, email) plutot que de les dupliquer — ce modele ne porte que ce qui est
 * specifique au site public.
 */
@Entity
@Table(name = "sites_vitrine")
public class SiteVitrine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long etablissementId;

    private boolean actif = false;

    /**
     * Le formulaire de pre-inscription est-il ouvert sur le site public ?
     *
     * Une ecole ne recrute pas toute l annee. Laisser le formulaire ouvert en fevrier
     * ferait attendre des familles pour rien, et remplirait le secretariat de demandes
     * qu il faudra refuser une par une.
     */
    private boolean preinscriptionActive = false;

    @Column(unique = true)
    private String slug;

    private String accroche;

    @Lob
    private String aPropos;

    @Lob
    private String infosAdmissions;

    private String emailContact;
    private String telephoneContact;
    private String facebookUrl;
    private String instagramUrl;

    /** Image de couverture du heros (chemin relatif via FileStorageService), utilisee si heroVideoUrl est vide. */
    private String heroImagePath;
    /** URL de video (YouTube/Vimeo) a integrer en fond du heros, prioritaire sur heroImagePath si presente. */
    private String heroVideoUrl;
    /** Couleur d'accent secondaire (hex), en complement de la couleur de marque de l'etablissement. */
    private String couleurSecondaire;
    /** CLASSIQUE, MODERNE ou ELEGANTE — determine la paire de polices chargee sur le site public. */
    private String police = "MODERNE";

    private LocalDateTime dateActivation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public boolean isPreinscriptionActive() { return preinscriptionActive; }
    public void setPreinscriptionActive(boolean preinscriptionActive) { this.preinscriptionActive = preinscriptionActive; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getAccroche() { return accroche; }
    public void setAccroche(String accroche) { this.accroche = accroche; }

    public String getaPropos() { return aPropos; }
    public void setaPropos(String aPropos) { this.aPropos = aPropos; }

    public String getInfosAdmissions() { return infosAdmissions; }
    public void setInfosAdmissions(String infosAdmissions) { this.infosAdmissions = infosAdmissions; }

    public String getEmailContact() { return emailContact; }
    public void setEmailContact(String emailContact) { this.emailContact = emailContact; }

    public String getTelephoneContact() { return telephoneContact; }
    public void setTelephoneContact(String telephoneContact) { this.telephoneContact = telephoneContact; }

    public String getFacebookUrl() { return facebookUrl; }
    public void setFacebookUrl(String facebookUrl) { this.facebookUrl = facebookUrl; }

    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String instagramUrl) { this.instagramUrl = instagramUrl; }

    public LocalDateTime getDateActivation() { return dateActivation; }
    public void setDateActivation(LocalDateTime dateActivation) { this.dateActivation = dateActivation; }

    public String getHeroImagePath() { return heroImagePath; }
    public void setHeroImagePath(String heroImagePath) { this.heroImagePath = heroImagePath; }

    public String getHeroVideoUrl() { return heroVideoUrl; }
    public void setHeroVideoUrl(String heroVideoUrl) { this.heroVideoUrl = heroVideoUrl; }

    public String getCouleurSecondaire() { return couleurSecondaire; }
    public void setCouleurSecondaire(String couleurSecondaire) { this.couleurSecondaire = couleurSecondaire; }

    public String getPolice() { return police; }
    public void setPolice(String police) { this.police = police; }
}
