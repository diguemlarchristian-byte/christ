package holyflame.administration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "classes")
public class Classe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String niveau;
    private String anneeScolaire;
    private Long etablissementId;
    // Parcours suivi par cette classe, en regime universitaire. Une classe y tient lieu de
    // promotion : « L1 Gestion A » suit la Licence Gestion. C'est ce qui permet de savoir, pour
    // un etudiant, quelles unites d'enseignement le concernent — un eleve appartient deja a une
    // classe, il n'y a donc rien a rattacher eleve par eleve.
    private Long parcoursId;

    private Long professeurTitulaireId;
    private Integer capacite;
    private String salle;
    @jakarta.persistence.Column(length = 2000)
    private String notes;

    public Classe() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getNiveau() {
        return niveau;
    }

    public void setNiveau(String niveau) {
        this.niveau = niveau;
    }

    public String getAnneeScolaire() {
        return anneeScolaire;
    }

    public void setAnneeScolaire(String anneeScolaire) {
        this.anneeScolaire = anneeScolaire;
    }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public Long getParcoursId() { return parcoursId; }
    public void setParcoursId(Long parcoursId) { this.parcoursId = parcoursId; }

    public Long getProfesseurTitulaireId() { return professeurTitulaireId; }
    public void setProfesseurTitulaireId(Long professeurTitulaireId) { this.professeurTitulaireId = professeurTitulaireId; }
    public Integer getCapacite() { return capacite; }
    public void setCapacite(Integer capacite) { this.capacite = capacite; }
    public String getSalle() { return salle; }
    public void setSalle(String salle) { this.salle = salle; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
