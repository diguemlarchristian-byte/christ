package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.MessagePrive;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.MessagerieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Messagerie de l'eleve vers ses enseignants (et l'administration), sur le meme modele que la
 * messagerie du Portail Parent (memes contacts, meme moteur de conversation) — un eleve n'avait
 * jusqu'ici aucun canal direct pour poser une question a un enseignant sans passer par son parent.
 */
@Controller
@RequestMapping("/portail/messages")
public class MessagerieEleveController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private MessagerieService messagerieService;

    private Optional<Eleve> resoudreEleveConnecte(Authentication auth) {
        String email = auth != null ? auth.getName() : "";
        Optional<Eleve> eleveOpt = eleveRepository.findByCompteEmail(email);
        if (eleveOpt.isEmpty()) eleveOpt = eleveRepository.findByEmailParent(email);
        return eleveOpt;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String avec, Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) {
            model.addAttribute("pasDeCompte", true);
            return "portail-messagerie";
        }
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();

        // Contacts reels : enseignants de la classe de l'eleve + Administration de l'etablissement
        // (meme construction que pour un parent : un eleve peut legitimement joindre les memes
        // interlocuteurs que ses propres parents).
        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsParent(List.of(eleve), etabId);

        List<Map<String, Object>> conversations = messagerieService.construireConversations(email, contactsParEmail);

        String correspondantActif = avec;
        if (correspondantActif == null && !conversations.isEmpty()) {
            correspondantActif = (String) conversations.get(0).get("email");
        }

        List<MessagePrive> fil = new java.util.ArrayList<>();
        Map<String, Object> contactActif = null;
        if (correspondantActif != null) {
            fil = messagerieService.chargerFilEtMarquerLu(email, correspondantActif);
            contactActif = contactsParEmail.get(correspondantActif);
            if (contactActif == null) {
                final String ca = correspondantActif;
                contactActif = conversations.stream()
                    .filter(c -> ca.equalsIgnoreCase((String) c.get("email"))).findFirst().orElse(null);
            }
        }

        long totalNonLus = conversations.stream().mapToLong(c -> (Long) c.get("nonLus")).sum();

        model.addAttribute("eleve", eleve);
        model.addAttribute("conversations", conversations);
        model.addAttribute("correspondantActif", correspondantActif);
        model.addAttribute("contactActif", contactActif);
        model.addAttribute("fil", fil);
        model.addAttribute("filAvecSeparateurs", messagerieService.avecSeparateursDate(fil));
        model.addAttribute("mediasPartages", messagerieService.mediasPartages(fil));
        model.addAttribute("totalNonLus", totalNonLus);
        model.addAttribute("monEmail", email);
        return "portail-messagerie";
    }

    @PostMapping("/envoyer")
    public String envoyer(@RequestParam String destinataire, @RequestParam(required = false) String contenu,
                           @RequestParam(required = false) MultipartFile pieceJointe,
                           Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";
        Optional<Eleve> eleveOpt = resoudreEleveConnecte(auth);
        if (eleveOpt.isEmpty()) return "redirect:/portail/messages";
        Eleve eleve = eleveOpt.get();
        Long etabId = eleve.getEtablissementId();

        // Le destinataire ne peut etre qu'un contact reellement legitime pour cet eleve (enseignant
        // de sa classe, ou administration de son etablissement) — meme garde-fou que cote parent.
        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsParent(List.of(eleve), etabId);
        if (!messagerieService.estContactAutorise(destinataire, contactsParEmail)) {
            ra.addFlashAttribute("erreurMsg", "Ce destinataire n'est pas un contact autorisé.");
            return "redirect:/portail/messages";
        }

        try {
            messagerieService.envoyerMessage(email, destinataire, contenu, pieceJointe, etabId);
        } catch (IOException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        }
        return "redirect:/portail/messages?avec=" + destinataire;
    }
}
