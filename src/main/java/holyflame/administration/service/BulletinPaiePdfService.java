package holyflame.administration.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.LigneSalaire;
import holyflame.administration.model.Parametre;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.SalaireMensuel;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.LigneSalaireRepository;
import holyflame.administration.repository.ParametreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Produit et date le bulletin de paie remis a l'employe.
 *
 * Le bulletin s'imprimait depuis le navigateur, a partir des taux en vigueur le jour de
 * l'impression. Six mois plus tard, apres un changement de taux de CNPS ou d'IRPP, la meme
 * page ne montrait plus ce qui avait effectivement ete remis : il ne restait aucune trace du
 * document lui-meme. En cas de contestation, l'employeur et l'employe n'avaient rien a
 * comparer.
 *
 * Le PDF est donc genere au moment exact ou le bulletin passe a « paye », puis range comme un
 * document du personnel. Comme pour le bulletin scolaire, il porte un code de verification —
 * ici une empreinte des montants decisifs, de sorte qu'un papier presente puisse etre
 * confronte a l'archive.
 *
 * Aucune ressource distante n'est chargee pendant la generation : l'application doit pouvoir
 * editer ce document sur une machine sans acces Internet.
 */
@Service
public class BulletinPaiePdfService {

    private static final String[] NOMS_MOIS = {"Janvier","Fevrier","Mars","Avril","Mai","Juin",
        "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"};
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    @Autowired private TemplateEngine templateEngine;
    @Autowired private LigneSalaireRepository ligneSalaireRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private EtablissementRepository etablissementRepository;

    /**
     * Empreinte du bulletin : elle depend des montants et de l'instant d'archivage, si bien
     * qu'un bulletin refait avec d'autres chiffres ne pourrait pas porter le meme code.
     * Elle ne remplace pas une signature — elle permet de rapprocher un papier d'une archive.
     */
    public String genererCode(SalaireMensuel s, LocalDateTime horodatage) {
        String empreinte = s.getId() + "|" + s.getMois() + "|" + s.getAnnee() + "|"
            + arrondi(s.getTotalBrut()) + "|" + arrondi(s.getTotalRetenuesSalariales()) + "|"
            + arrondi(s.getNetAPayer()) + "|" + horodatage;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(empreinte.getBytes(StandardCharsets.UTF_8));
            String court = HexFormat.of().formatHex(hash).substring(0, 8).toUpperCase();
            return "PAIE-" + s.getAnnee() + String.format("%02d", s.getMois()) + "-" + court;
        } catch (Exception e) {
            // Un algorithme de hachage standard absent de la JVM ne doit pas empecher un
            // paiement : on retombe sur un code lisible, unique par bulletin et par instant.
            return "PAIE-" + s.getAnnee() + String.format("%02d", s.getMois()) + "-" + s.getId()
                 + "-" + horodatage.toLocalTime().toSecondOfDay();
        }
    }

    public byte[] genererPdf(SalaireMensuel s, String codeVerification, LocalDateTime horodatage) {
        Context contexte = new Context();
        contexte.setVariables(donnees(s, codeVerification, horodatage));
        String html = templateEngine.process("bulletin-paie-pdf", contexte);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(sortie);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la generation du PDF du bulletin de paie.", e);
        }
        return sortie.toByteArray();
    }

    private Map<String, Object> donnees(SalaireMensuel s, String codeVerification, LocalDateTime horodatage) {
        Personnel personnel = s.getPersonnel();
        Long etabId = personnel != null ? personnel.getEtablissementId() : null;

        List<LigneSalaire> lignes = ligneSalaireRepository.findBySalaireMensuelIdOrderByOrdreAsc(s.getId());
        Map<String, List<LigneSalaire>> parSection = lignes.stream()
            .collect(Collectors.groupingBy(LigneSalaire::getSection, LinkedHashMap::new, Collectors.toList()));

        Map<String, String> params = etabId == null ? Map.of()
            : parametreRepository.findByEtablissementId(etabId).stream()
                .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));

        Etablissement etab = etabId == null ? null : etablissementRepository.findById(etabId).orElse(null);

        Long anciennete = personnel != null && personnel.getDateEmbauche() != null && s.getPeriodeFin() != null
            ? Period.between(personnel.getDateEmbauche(), s.getPeriodeFin()).toTotalMonths()
            : null;

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("bulletin", s);
        d.put("personnel", personnel);
        d.put("lignesGains", parSection.getOrDefault("GAIN", List.of()));
        d.put("lignesRetenues", parSection.getOrDefault("RETENUE_SALARIALE", List.of()));
        d.put("lignesCharges", parSection.getOrDefault("CHARGE_PATRONALE", List.of()));
        d.put("anciennete", anciennete);
        d.put("nomMois", s.getMois() >= 1 && s.getMois() <= 12 ? NOMS_MOIS[s.getMois() - 1] : String.valueOf(s.getMois()));
        d.put("nomEtab", etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        d.put("adresseEtab", etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        d.put("telEtab", params.getOrDefault("TELEPHONE_ECOLE", ""));
        d.put("monnaie", params.getOrDefault("MONNAIE", "FCFA"));
        d.put("codeVerification", codeVerification);
        d.put("horodatage", horodatage.format(HORODATAGE));
        return d;
    }

    private long arrondi(Double d) {
        return d == null ? 0 : Math.round(d);
    }
}
