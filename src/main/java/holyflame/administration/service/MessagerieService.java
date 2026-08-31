package holyflame.administration.service;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.MessagePrive;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.MessagePriveRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class MessagerieService {

    @Autowired private MessagePriveRepository messagePriveRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EnseignantAutorisationRepository enseignantAutorisationRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private HorlogeService horlogeService;
    @Autowired private EmailService emailService;

    public Map<String, Object> creerContact(String email, String nom, String role) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("email", email);
        c.put("nom", nom);
        c.put("role", role);
        c.put("matieres", new ArrayList<String>());
        return c;
    }

    /**
     * Construit les contacts reels d'un parent : enseignants des classes de ses enfants
     * + un contact Administration. Partage entre la messagerie et le resume du tableau de bord.
     */
    public Map<String, Map<String, Object>> construireContactsParent(List<Eleve> enfants, Long etabId) {
        Map<String, Map<String, Object>> contactsParEmail = new LinkedHashMap<>();
        if (etabId == null) return contactsParEmail;

        List<Long> classeIds = enfants.stream()
            .filter(e -> e.getClasse() != null).map(e -> e.getClasse().getId()).distinct().toList();

        List<EnseignantAutorisation> autorisations = enseignantAutorisationRepository.findByEtablissementId(etabId).stream()
            .filter(a -> classeIds.contains(a.getClasseId())).toList();

        for (EnseignantAutorisation autorisation : autorisations) {
            Utilisateur enseignant = utilisateurRepository.findById(autorisation.getEnseignantId()).orElse(null);
            if (enseignant == null || enseignant.getEmail() == null) continue;
            Matiere matiere = matiereRepository.findById(autorisation.getMatiereId()).orElse(null);
            Map<String, Object> contact = contactsParEmail.computeIfAbsent(enseignant.getEmail(), k -> creerContact(
                enseignant.getEmail(),
                (enseignant.getPrenom() != null ? enseignant.getPrenom() + " " : "") + (enseignant.getNom() != null ? enseignant.getNom() : enseignant.getEmail()),
                "Enseignant"));
            if (matiere != null) {
                @SuppressWarnings("unchecked")
                List<String> matieres = (List<String>) contact.get("matieres");
                if (!matieres.contains(matiere.getNom())) matieres.add(matiere.getNom());
            }
        }

        for (Utilisateur admin : utilisateurRepository.findByEtablissementId(etabId)) {
            if (("ADMIN".equals(admin.getRole()) || "SECRETAIRE".equals(admin.getRole())) && admin.getEmail() != null
                    && !contactsParEmail.containsKey(admin.getEmail())) {
                contactsParEmail.put(admin.getEmail(), creerContact(admin.getEmail(), "Administration", "Secretariat"));
                break;
            }
        }

        return contactsParEmail;
    }

    /**
     * Construit les contacts reels d'un membre du staff : parents des eleves qu'il peut
     * legitimement contacter (ses classes autorisees pour un enseignant, tout l'etablissement
     * pour admin/secretaire). Partage entre l'affichage de la messagerie et la verification
     * d'autorisation a l'envoi.
     */
    public Map<String, Map<String, Object>> construireContactsStaff(Utilisateur moi, Long etabId) {
        Map<String, Map<String, Object>> contactsParEmail = new LinkedHashMap<>();
        if (etabId == null || moi == null) return contactsParEmail;

        List<Eleve> elevesConcernes;
        if ("ENSEIGNANT".equals(moi.getRole())) {
            List<Long> classeIds = enseignantAutorisationRepository.findByEnseignantIdAndEtablissementId(moi.getId(), etabId).stream()
                .map(EnseignantAutorisation::getClasseId).distinct().toList();
            elevesConcernes = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
                .filter(e -> e.getClasse() != null && classeIds.contains(e.getClasse().getId()))
                .toList();
        } else {
            elevesConcernes = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        }

        Map<String, List<Eleve>> elevesParParent = new LinkedHashMap<>();
        for (Eleve e : elevesConcernes) {
            if (e.getEmailParent() == null || e.getEmailParent().isBlank()) continue;
            elevesParParent.computeIfAbsent(e.getEmailParent(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Eleve>> entry : elevesParParent.entrySet()) {
            String nomsEnfants = entry.getValue().stream()
                .map(e -> e.getPrenom() + " " + e.getNom()).distinct().reduce((a, b) -> a + ", " + b).orElse("");
            String classesEnfants = entry.getValue().stream()
                .map(Eleve::getClasse).filter(Objects::nonNull).map(Classe::getNom).distinct()
                .reduce((a, b) -> a + ", " + b).orElse("");
            contactsParEmail.put(entry.getKey(), creerContact(
                entry.getKey(), "Parent de " + nomsEnfants, classesEnfants.isBlank() ? "Parent" : classesEnfants));
        }
        return contactsParEmail;
    }

    /** Verifie qu'un destinataire fait bien partie des contacts autorises (comparaison insensible a la casse). */
    public boolean estContactAutorise(String destinataire, Map<String, Map<String, Object>> contactsParEmail) {
        if (destinataire == null) return false;
        return contactsParEmail.keySet().stream().anyMatch(email -> email.equalsIgnoreCase(destinataire));
    }

    public List<Map<String, Object>> construireConversations(String monEmail, Map<String, Map<String, Object>> contactsParEmail) {
        List<MessagePrive> mesMessages = messagePriveRepository.findAllConcernant(monEmail);
        Map<String, List<MessagePrive>> parCorrespondant = new LinkedHashMap<>();
        for (MessagePrive m : mesMessages) {
            String correspondant = monEmail.equalsIgnoreCase(m.getExpediteurEmail()) ? m.getDestinataireEmail() : m.getExpediteurEmail();
            parCorrespondant.computeIfAbsent(correspondant, k -> new ArrayList<>()).add(m);
        }

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (Map.Entry<String, List<MessagePrive>> entry : parCorrespondant.entrySet()) {
            String correspondant = entry.getKey();
            List<MessagePrive> msgs = entry.getValue();
            MessagePrive dernier = msgs.get(msgs.size() - 1);
            long nonLus = msgs.stream().filter(m -> !m.isLu() && correspondant.equalsIgnoreCase(m.getExpediteurEmail())).count();

            Map<String, Object> contactInfo = contactsParEmail.get(correspondant);
            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("email", correspondant);
            conv.put("nom", contactInfo != null ? contactInfo.get("nom") : correspondant);
            conv.put("role", contactInfo != null ? contactInfo.get("role") : "");
            conv.put("dernierMessage", dernier.getContenu());
            conv.put("dernierDate", dernier.getDateEnvoi());
            conv.put("nonLus", nonLus);
            conversations.add(conv);
        }
        conversations.sort(Comparator.comparing((Map<String, Object> c) -> (LocalDateTime) c.get("dernierDate")).reversed());

        for (Map<String, Object> contact : contactsParEmail.values()) {
            String contactEmail = (String) contact.get("email");
            boolean dejaPresent = conversations.stream().anyMatch(c -> contactEmail.equalsIgnoreCase((String) c.get("email")));
            if (!dejaPresent) {
                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("email", contactEmail);
                conv.put("nom", contact.get("nom"));
                conv.put("role", contact.get("role"));
                conv.put("dernierMessage", null);
                conv.put("dernierDate", null);
                conv.put("nonLus", 0L);
                conversations.add(conv);
            }
        }
        return conversations;
    }

    public List<MessagePrive> chargerFilEtMarquerLu(String monEmail, String correspondant) {
        List<MessagePrive> fil = messagePriveRepository.findConversation(monEmail, correspondant);
        for (MessagePrive m : fil) {
            if (!m.isLu() && correspondant.equalsIgnoreCase(m.getExpediteurEmail())) {
                m.setLu(true);
                messagePriveRepository.save(m);
            }
        }
        return fil;
    }

    public List<MessagePrive> mediasPartages(List<MessagePrive> fil) {
        return fil.stream().filter(m -> m.getPieceJointeChemin() != null).toList();
    }

    public List<Map<String, Object>> avecSeparateursDate(List<MessagePrive> fil) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate dernierJour = null;
        LocalDate aujourdHui = horlogeService.aujourdHui();
        for (MessagePrive m : fil) {
            LocalDate jour = m.getDateEnvoi().toLocalDate();
            Map<String, Object> row = new LinkedHashMap<>();
            if (dernierJour == null || !jour.equals(dernierJour)) {
                String label;
                if (jour.equals(aujourdHui)) label = "AUJOURD'HUI";
                else if (jour.equals(aujourdHui.minusDays(1))) label = "HIER";
                else label = jour.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
                row.put("separateur", label);
            } else {
                row.put("separateur", null);
            }
            row.put("message", m);
            result.add(row);
            dernierJour = jour;
        }
        return result;
    }

    /** Types de pieces jointes acceptes en messagerie : jamais de HTML/SVG (executables dans un
        navigateur si ouverts directement depuis /uploads, servi sans authentification). */
    private static final java.util.Set<String> TYPES_PIECE_JOINTE_AUTORISES = java.util.Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "application/pdf", "text/plain",
        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public void envoyerMessage(String expediteur, String destinataire, String contenu, MultipartFile pieceJointe,
            Long etablissementId) throws IOException {
        boolean aTexte = contenu != null && !contenu.isBlank();
        boolean aPieceJointe = pieceJointe != null && !pieceJointe.isEmpty();
        if (!aTexte && !aPieceJointe) return;
        if (aPieceJointe && !TYPES_PIECE_JOINTE_AUTORISES.contains(pieceJointe.getContentType())) {
            throw new IOException("Type de fichier non autorisé. Formats acceptés : images, PDF, Word, Excel, texte.");
        }

        MessagePrive m = new MessagePrive();
        m.setExpediteurEmail(expediteur);
        m.setDestinataireEmail(destinataire);
        m.setContenu(aTexte && contenu != null ? contenu.trim() : "");
        m.setDateEnvoi(horlogeService.maintenant(etablissementId));
        m.setLu(false);
        m.setEtablissementId(etablissementId);
        if (aPieceJointe) {
            String path = fileStorageService.store(pieceJointe, "messagerie");
            m.setPieceJointeNom(pieceJointe.getOriginalFilename());
            m.setPieceJointeChemin(path);
            m.setPieceJointeType(pieceJointe.getContentType());
            m.setPieceJointeTaille(pieceJointe.getSize());
        }
        messagePriveRepository.save(m);
        notifierDestinataire(expediteur, destinataire, m);
    }

    // Le destinataire (parent ou personnel) n'avait aucun moyen d'apprendre qu'on lui avait
    // ecrit sans se reconnecter par hasard sur la messagerie — cause frequente de "vous ne
    // repondez jamais" alors que le message attendait, non lu. Notification best-effort : un
    // envoi qui echoue (service non configure, etc.) ne doit jamais faire echouer le message.
    private void notifierDestinataire(String expediteur, String destinataire, MessagePrive m) {
        try {
            String nomExpediteur = utilisateurRepository.findByEmail(expediteur)
                .map(u -> (u.getPrenom() != null ? u.getPrenom() + " " : "") + (u.getNom() != null ? u.getNom() : ""))
                .filter(n -> !n.isBlank())
                .orElse(expediteur);
            String apercu = m.getContenu() != null && !m.getContenu().isBlank()
                ? (m.getContenu().length() > 140 ? m.getContenu().substring(0, 140) + "..." : m.getContenu())
                : "(pièce jointe)";
            String sujet = "Nouveau message de " + nomExpediteur;
            String corps = "<p>Bonjour,</p>"
                + "<p><strong>" + nomExpediteur + "</strong> vous a envoyé un nouveau message :</p>"
                + "<blockquote style=\"margin:12px 0;padding:8px 12px;border-left:3px solid #ccc;color:#444;\">" + apercu + "</blockquote>"
                + "<p>Connectez-vous à votre messagerie pour répondre.</p>";
            emailService.envoyer(destinataire, sujet, corps);
        } catch (Exception ignored) {
        }
    }
}
