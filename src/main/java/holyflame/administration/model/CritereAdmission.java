package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un critere de la grille d'admission, tel que l'etablissement le definit.
 *
 * Une grille ecrite en dur dans le logiciel ne conviendrait a personne : un lycee regarde
 * le bulletin de l'annee precedente et la conduite, une faculte regarde le baccalaureat et
 * une lettre de motivation, une ecole professionnelle fait passer un entretien. Chacun
 * ecrit donc ses propres lignes, avec le poids qu'il leur donne.
 *
 * Le poids n'est pas un pourcentage : c'est un coefficient. Trois criteres de poids 2, 1 et
 * 1 donnent une note sur vingt ponderee, sans qu'un etablissement ait a verifier que ses
 * poids totalisent cent.
 */
@Entity
@Table(name = "criteres_admission")
public class CritereAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long etablissementId;

    @Column(nullable = false)
    private String libelle;

    /** Ce que le jury doit regarder. Facultatif, mais il evite que chacun note autre chose. */
    @Column(length = 500)
    private String precision;

    /** Coefficient du critere dans la note finale. */
    @Column(nullable = false)
    private Double poids = 1.0;

    /** Rang d'affichage dans la grille. */
    @Column(nullable = false)
    private Integer ordre = 0;

    /**
     * Un critere retire ne disparait pas.
     *
     * Les dossiers deja notes gardent leurs notes : effacer le critere effacerait aussi le
     * raisonnement d'une commission passee, et une decision qu'on ne peut plus expliquer
     * est une decision indefendable.
     */
    @Column(nullable = false)
    private boolean actif = true;

    public CritereAdmission() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }

    public String getPrecision() { return precision; }
    public void setPrecision(String precision) { this.precision = precision; }

    public Double getPoids() { return poids; }
    public void setPoids(Double poids) { this.poids = poids; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
