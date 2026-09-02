package holyflame.administration.repository;

import holyflame.administration.model.MessagePrive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessagePriveRepository extends JpaRepository<MessagePrive, Long> {

    @Query("SELECT m FROM MessagePrive m WHERE m.expediteurEmail = :email OR m.destinataireEmail = :email ORDER BY m.dateEnvoi ASC")
    List<MessagePrive> findAllConcernant(@Param("email") String email);

    @Query("SELECT m FROM MessagePrive m WHERE (m.expediteurEmail = :email1 AND m.destinataireEmail = :email2) "
         + "OR (m.expediteurEmail = :email2 AND m.destinataireEmail = :email1) ORDER BY m.dateEnvoi ASC")
    List<MessagePrive> findConversation(@Param("email1") String email1, @Param("email2") String email2);

    long countByDestinataireEmailAndExpediteurEmailAndLuFalse(String destinataireEmail, String expediteurEmail);

    /** Total des messages recus non lus, tous correspondants confondus (badge d'accueil). */
    long countByDestinataireEmailAndLuFalse(String destinataireEmail);
}
