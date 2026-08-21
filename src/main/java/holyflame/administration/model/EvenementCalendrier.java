package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Marqueur ponctuel et personnalisable sur le calendrier scolaire (echeance de devoir, jour
 * exceptionnel, etc.) — purement informatif, sans impact sur le decompte des jours d'ecole
 * ni sur les {@link PeriodeCalendrier}. Coexiste visuellement avec le trimestre/vacances du
 * jour concerne plutot que de le remplacer.
 */
@Entity
@Table(name = "evenements_calendrier")
public class EvenementCalendrier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    /** Libre : DEVOIR, JOUR_EXCEPTIONNEL, ou toute etiquette choisie par l'etablissement. */
    private String type;

    @Column(nullable = false)
    private LocalDate date;

    /** Couleur hex choisie par l'etablissement (ex: #db5e1b), affichee comme pastille sur le jour. */
    @Column(nullable = false)
    private String couleur = "#db5e1b";

    private String description;

    private String anneeScolaire;

    private Long etablissementId;

    public EvenementCalendrier() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getCouleur() { return couleur; }
    public void setCouleur(String couleur) { this.couleur = couleur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
