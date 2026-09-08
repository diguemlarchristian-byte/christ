package holyflame.administration.model;

import jakarta.persistence.*;

/**
 * Unite d'enseignement (UE) : l'unite qui porte les credits et qui se valide.
 *
 * C'est la difference de fond avec le regime scolaire. Une matiere scolaire porte un
 * coefficient qui pondere une moyenne generale ; une UE porte des credits qui s'acquierent
 * en bloc et definitivement. Un etudiant ne « passe » pas une annee, il accumule des credits.
 */
@Entity
// Table distincte de "unites_enseignement", qui appartient au module universitaire de
// « Copy of administration - Copie » et repose sur un autre modele (Faculte > Departement >
// Filiere). Partager la table forcait chaque modele a renseigner la cle etrangere de l'autre,
// et bloquait les deux en ecriture. Tant que l'arbitrage entre les deux modeles n'est pas fait,
// chacun ecrit chez lui.
@Table(name = "parcours_unites")
public class UniteEnseignement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code de la maquette, tel qu'il figure sur le releve de notes : « UE-L1-INF-01 ».
     *  Obligatoire : une unite sans code serait impossible a designer sur un document officiel.
     *  Le service en genere un quand l'administrateur n'en saisit pas. */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String intitule;

    /** Credits attribues en totalite des que l'unite est validee — jamais au prorata. */
    @Column(nullable = false)
    private Integer credits;

    /** Rang du semestre dans le parcours : 1 a 6 pour une licence. */
    @Column(nullable = false)
    private Integer semestre;

    /** FONDAMENTALE, METHODOLOGIQUE, DECOUVERTE, TRANSVERSALE. */
    private String type = "FONDAMENTALE";

    /** Parcours d'appartenance : une unite n'existe jamais hors d'un parcours. */
    @Column(nullable = false)
    private Long parcoursId;

    @Column(nullable = false)
    private Long etablissementId;

    public UniteEnseignement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getIntitule() { return intitule; }
    public void setIntitule(String intitule) { this.intitule = intitule; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getParcoursId() { return parcoursId; }
    public void setParcoursId(Long parcoursId) { this.parcoursId = parcoursId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
