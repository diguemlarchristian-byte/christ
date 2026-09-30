package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * La note donnee a un dossier sur un critere de la grille.
 *
 * Elle est conservee critere par critere, et non repliee en un seul total. Un candidat
 * refuse demande pourquoi, et « 11,4 sur 20 » ne repond pas : ce qui repond, c'est de
 * pouvoir montrer que le bulletin valait 8 et l'entretien 15. Une commission qui ne peut
 * plus detailler sa decision ne peut plus la defendre.
 */
@Entity
@Table(name = "notes_admission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"demandeId", "critereId"}),
    indexes = @Index(name = "idx_note_admission_demande", columnList = "demandeId"))
public class NoteAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long demandeId;

    @Column(nullable = false)
    private Long critereId;

    /** Sur vingt, comme toutes les notes du logiciel. */
    @Column(nullable = false)
    private Double note;

    public NoteAdmission() {
    }

    public NoteAdmission(Long demandeId, Long critereId, Double note) {
        this.demandeId = demandeId;
        this.critereId = critereId;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }

    public Long getCritereId() { return critereId; }
    public void setCritereId(Long critereId) { this.critereId = critereId; }

    public Double getNote() { return note; }
    public void setNote(Double note) { this.note = note; }
}
