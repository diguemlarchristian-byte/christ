package holyflame.administration.model;

import jakarta.persistence.*;

/**
 * Element constitutif (ECUE) : la matiere reellement enseignee a l'interieur d'une unite.
 *
 * C'est le niveau auquel les enseignants saisissent les notes. Les credits, eux, restent
 * portes par l'unite : un ECUE n'en attribue jamais. Son coefficient sert uniquement a
 * ponderer sa note dans la moyenne de l'unite qui le contient.
 */
@Entity
@Table(name = "elements_constitutifs")
public class ElementConstitutif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String intitule;

    /** Poids de cet element dans la moyenne de son unite. */
    @Column(nullable = false)
    private Double coefficient = 1.0;

    /** Volume horaire annonce dans la maquette, a titre indicatif. */
    private Integer volumeHoraire;

    @Column(nullable = false)
    private Long uniteEnseignementId;

    /** Enseignant en charge — null tant que l'affectation n'est pas faite. */
    private Long enseignantId;

    @Column(nullable = false)
    private Long etablissementId;

    public ElementConstitutif() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIntitule() { return intitule; }
    public void setIntitule(String intitule) { this.intitule = intitule; }
    public Double getCoefficient() { return coefficient; }
    public void setCoefficient(Double coefficient) { this.coefficient = coefficient; }
    public Integer getVolumeHoraire() { return volumeHoraire; }
    public void setVolumeHoraire(Integer volumeHoraire) { this.volumeHoraire = volumeHoraire; }
    public Long getUniteEnseignementId() { return uniteEnseignementId; }
    public void setUniteEnseignementId(Long uniteEnseignementId) { this.uniteEnseignementId = uniteEnseignementId; }
    public Long getEnseignantId() { return enseignantId; }
    public void setEnseignantId(Long enseignantId) { this.enseignantId = enseignantId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
