package holyflame.administration.repository;

import holyflame.administration.model.NoteAdmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteAdmissionRepository extends JpaRepository<NoteAdmission, Long> {

    List<NoteAdmission> findByDemandeId(Long demandeId);

    List<NoteAdmission> findByDemandeIdIn(List<Long> demandeIds);

    void deleteByDemandeId(Long demandeId);
}
