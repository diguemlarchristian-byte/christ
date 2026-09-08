package holyflame.administration.model;

import jakarta.persistence.*;

/**
 * Filiere diplomante d'un etablissement en regime LMD : « Licence Gestion », « Master Informatique ».
 *
 * Le parcours porte la duree du cursus et le diplome delivre. Il joue, cote universite, le role
 * que le niveau de classe joue cote scolaire : c'est lui qui determine quelles unites
 * d'enseignement un etudiant doit valider.
 */
@Entity
@Table(name = "parcours")
public class Parcours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String libelle;

    /** LICENCE, MASTER, DOCTORAT — le diplome vise a l'issue du parcours. */
    private String diplome;

    /** Nombre de semestres du cursus : 6 pour une licence, 4 pour un master. */
    private Integer nbSemestres;

    private String description;

    @Column(nullable = false)
    private Long etablissementId;

    /** Un parcours ferme n'accepte plus de nouvelles inscriptions, sans effacer l'historique. */
    private boolean actif = true;

    public Parcours() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getDiplome() { return diplome; }
    public void setDiplome(String diplome) { this.diplome = diplome; }
    public Integer getNbSemestres() { return nbSemestres; }
    public void setNbSemestres(Integer nbSemestres) { this.nbSemestres = nbSemestres; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
}
