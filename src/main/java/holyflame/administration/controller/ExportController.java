package holyflame.administration.controller;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.model.Depense;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Note;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.CategorieComptableRepository;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.util.AnneeScolaireUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/export")
public class ExportController {

    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private EtablissementService etablissementService;

    // ── Styles partagés ──────────────────────────────────────────
    private XSSFCellStyle makeHeaderStyle(XSSFWorkbook wb, byte r, byte g, byte b) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{r, g, b}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        f.setBold(true);
        s.setFont(f);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    @GetMapping("/paiements/excel")
    public void exportPaiements(HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("Paiements");

        // Styles
        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)27,(byte)54,(byte)93}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(new XSSFColor(new byte[]{(byte)255,(byte)255,(byte)255}, null));
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        XSSFCellStyle totalStyle = wb.createCellStyle();
        totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)240,(byte)244,(byte)248}, null));
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont totalFont = wb.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);

        // Titre
        Row titre = sheet.createRow(0);
        Cell titreCell = titre.createCell(0);
        titreCell.setCellValue("RELEVÉ DES PAIEMENTS – HolyFlame");
        XSSFCellStyle titreStyle = wb.createCellStyle();
        XSSFFont titreFont = wb.createFont();
        titreFont.setBold(true);
        titreFont.setFontHeightInPoints((short)14);
        titreStyle.setFont(titreFont);
        titreCell.setCellStyle(titreStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // En-têtes colonnes
        String[] headers = {"N° Reçu", "Élève", "Classe", "Montant (FCFA)", "Type", "Mode", "Date"};
        Row hRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Données
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        int rowNum = 3;
        double total = 0;
        for (Paiement p : paiements) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getRecuNumero() != null ? p.getRecuNumero() : "");
            row.createCell(1).setCellValue(
                p.getEleve() != null ? p.getEleve().getNom() + " " + p.getEleve().getPrenom() : "");
            row.createCell(2).setCellValue(
                p.getEleve() != null && p.getEleve().getClasse() != null
                    ? p.getEleve().getClasse().getNom() : "");
            double montant = p.getMontantVerse() != null ? p.getMontantVerse() : 0;
            row.createCell(3).setCellValue(montant);
            total += montant;
            row.createCell(4).setCellValue(p.getTypePaiement() != null ? p.getTypePaiement() : "");
            row.createCell(5).setCellValue(p.getModePaiement() != null ? p.getModePaiement() : "");
            row.createCell(6).setCellValue(
                p.getDatePaiement() != null ? p.getDatePaiement().format(fmt) : "");
        }

        // Total
        Row totalRow = sheet.createRow(rowNum + 1);
        Cell labelCell = totalRow.createCell(2);
        labelCell.setCellValue("TOTAL ENCAISSÉ");
        labelCell.setCellStyle(totalStyle);
        Cell totalCell = totalRow.createCell(3);
        totalCell.setCellValue(total);
        totalCell.setCellStyle(totalStyle);

        // Auto-size
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"paiements-holyflame.xlsx\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

    // ── Export liste des élèves ──────────────────────────────────
    @GetMapping("/eleves/excel")
    public void exportEleves(HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> eleves = etabId != null
            ? eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId)
            : List.of();

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("Élèves");
        XSSFCellStyle hStyle = makeHeaderStyle(wb, (byte)13, (byte)71, (byte)161);

        Row titre = sheet.createRow(0);
        Cell titreCell = titre.createCell(0);
        titreCell.setCellValue("LISTE DES ÉLÈVES");
        XSSFCellStyle titreStyle = wb.createCellStyle();
        XSSFFont titreFont = wb.createFont(); titreFont.setBold(true); titreFont.setFontHeightInPoints((short)13);
        titreStyle.setFont(titreFont); titreCell.setCellStyle(titreStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        String[] headers = {"Matricule","Nom","Prénom","Classe","Date naissance","Tél. parent","Email parent","Statut"};
        Row hRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hStyle);
        }

        DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int row = 3;
        for (Eleve e : eleves) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(e.getMatricule() != null ? e.getMatricule() : "");
            r.createCell(1).setCellValue(e.getNom() != null ? e.getNom() : "");
            r.createCell(2).setCellValue(e.getPrenom() != null ? e.getPrenom() : "");
            r.createCell(3).setCellValue(e.getClasse() != null ? e.getClasse().getNom() : "");
            r.createCell(4).setCellValue(e.getDateNaissance() != null ? e.getDateNaissance().format(fmtDate) : "");
            r.createCell(5).setCellValue(e.getTelephoneParent() != null ? e.getTelephoneParent() : "");
            r.createCell(6).setCellValue(e.getEmailParent() != null ? e.getEmailParent() : "");
            r.createCell(7).setCellValue(e.getStatutInscription() != null ? e.getStatutInscription() : "");
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"eleves.xlsx\"");
        wb.write(response.getOutputStream()); wb.close();
    }

    // ── Export notes par trimestre ───────────────────────────────
    @GetMapping("/notes/excel")
    public void exportNotes(@RequestParam(defaultValue = "1") Integer trimestre,
                            HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Note> notes = etabId != null
            ? noteRepository.findByEtablissementId(etabId).stream()
                .filter(n -> trimestre.equals(n.getTrimestre())).collect(Collectors.toList())
            : List.of();

        // Un enseignant n'exporte que les notes des matieres/classes qu'il enseigne lui-meme ;
        // ADMIN/SECRETAIRE conservent la vue complete sur l'etablissement.
        Utilisateur utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        if (utilisateurConnecte != null && "ENSEIGNANT".equals(utilisateurConnecte.getRole()) && etabId != null) {
            var autorisations = autorisationRepository.findByEnseignantIdAndEtablissementId(utilisateurConnecte.getId(), etabId);
            notes = notes.stream()
                .filter(n -> n.getMatiere() != null && n.getEleve() != null && n.getEleve().getClasse() != null
                    && autorisations.stream().anyMatch(a -> a.getMatiereId().equals(n.getMatiere().getId())
                        && a.getClasseId().equals(n.getEleve().getClasse().getId())))
                .collect(Collectors.toList());
        }

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("Notes T" + trimestre);
        XSSFCellStyle hStyle = makeHeaderStyle(wb, (byte)21, (byte)101, (byte)112);

        Row titre = sheet.createRow(0);
        Cell titreCell = titre.createCell(0);
        titreCell.setCellValue("RELEVÉ DES NOTES – Trimestre " + trimestre);
        XSSFCellStyle titreStyle = wb.createCellStyle();
        XSSFFont titreFont = wb.createFont(); titreFont.setBold(true); titreFont.setFontHeightInPoints((short)13);
        titreStyle.setFont(titreFont); titreCell.setCellStyle(titreStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        String[] headers = {"Élève","Classe","Matière","Note /20","Coefficient","Type","Date"};
        Row hRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(hStyle);
        }

        DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int row = 3;
        for (Note n : notes) {
            Row r = sheet.createRow(row++);
            r.createCell(0).setCellValue(n.getEleve() != null ? n.getEleve().getNom() + " " + n.getEleve().getPrenom() : "");
            r.createCell(1).setCellValue(n.getEleve() != null && n.getEleve().getClasse() != null ? n.getEleve().getClasse().getNom() : "");
            r.createCell(2).setCellValue(n.getMatiere() != null ? n.getMatiere().getNom() : "");
            r.createCell(3).setCellValue(n.getValeur() != null ? n.getValeur() : 0);
            r.createCell(4).setCellValue(n.getCoefficient() != null ? n.getCoefficient() : 0);
            r.createCell(5).setCellValue(n.getType() != null ? n.getType() : "");
            r.createCell(6).setCellValue(n.getDateEvaluation() != null ? n.getDateEvaluation().format(fmtDate) : "");
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"notes-t" + trimestre + ".xlsx\"");
        wb.write(response.getOutputStream()); wb.close();
    }

    // ── Export des rapports financiers (Comptable/Tresorier) ──────
    // Le module Finances > Rapports n'offrait que de la consultation a l'ecran : rien a remettre
    // a une direction, un auditeur ou le fisc sans capture d'ecran. Deux feuilles : le compte de
    // resultat annuel simplifie, et la balance des comptes (chaque poste du plan comptable
    // mouvemente cette annee, avec son total) — la premiere chose qu'un comptable verifie.
    @GetMapping("/rapports/excel")
    public void exportRapports(@RequestParam String annee, HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Depense> toutesDepenses = etabId != null ? depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId) : List.of();
        List<Paiement> tousPaiements = etabId != null ? paiementRepository.findByEtablissementId(etabId) : List.of();

        double produitsViaPaiements = tousPaiements.stream()
            .filter(p -> p.getDatePaiement() != null && annee.equals(AnneeScolaireUtil.pour(p.getDatePaiement().toLocalDate())))
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        double produitsViaDepenses = toutesDepenses.stream()
            .filter(d -> "PRODUIT".equals(d.getSens()) && annee.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double totalProduits = produitsViaPaiements + produitsViaDepenses;
        double totalCharges = toutesDepenses.stream()
            .filter(d -> "CHARGE".equals(d.getSens()) && annee.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double resultatNet = totalProduits - totalCharges;

        List<CategorieComptable> planActif = etabId != null
            ? categorieComptableRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId) : List.of();
        Map<Long, Double> totalParPosteId = toutesDepenses.stream()
            .filter(d -> annee.equals(d.getAnneeScolaire()) && d.getCategorieComptable() != null)
            .collect(Collectors.groupingBy(d -> d.getCategorieComptable().getId(),
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFCellStyle hStyle = makeHeaderStyle(wb, (byte)0, (byte)35, (byte)111);
        XSSFCellStyle titreStyle = wb.createCellStyle();
        XSSFFont titreFont = wb.createFont(); titreFont.setBold(true); titreFont.setFontHeightInPoints((short)13);
        titreStyle.setFont(titreFont);
        XSSFCellStyle totalStyle = wb.createCellStyle();
        totalStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)240,(byte)244,(byte)248}, null));
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont totalFont = wb.createFont(); totalFont.setBold(true);
        totalStyle.setFont(totalFont);

        // Feuille 1 : Compte de resultat
        XSSFSheet sheetResultat = wb.createSheet("Compte de resultat");
        Cell titreResultat = sheetResultat.createRow(0).createCell(0);
        titreResultat.setCellValue("COMPTE DE RÉSULTAT SIMPLIFIÉ — Année " + annee);
        titreResultat.setCellStyle(titreStyle);
        sheetResultat.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        String[][] lignesResultat = {
            {"Total produits (scolarité, dons, autres recettes)", String.valueOf(totalProduits)},
            {"Total charges (dépenses, salaires, fonctionnement)", String.valueOf(totalCharges)},
            {"RÉSULTAT NET", String.valueOf(resultatNet)}
        };
        int rowR = 2;
        for (String[] ligne : lignesResultat) {
            Row row = sheetResultat.createRow(rowR++);
            row.createCell(0).setCellValue(ligne[0]);
            Cell cVal = row.createCell(1);
            cVal.setCellValue(Double.parseDouble(ligne[1]));
            if (ligne[0].equals("RÉSULTAT NET")) { row.getCell(0).setCellStyle(totalStyle); cVal.setCellStyle(totalStyle); }
        }
        sheetResultat.autoSizeColumn(0); sheetResultat.autoSizeColumn(1);

        // Feuille 2 : Balance des comptes
        XSSFSheet sheetBalance = wb.createSheet("Balance des comptes");
        Cell titreBalance = sheetBalance.createRow(0).createCell(0);
        titreBalance.setCellValue("BALANCE DES COMPTES — Année " + annee);
        titreBalance.setCellStyle(titreStyle);
        sheetBalance.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        String[] headersBalance = {"Code", "Libellé", "Sens", "Montant (FCFA)"};
        Row hRowBalance = sheetBalance.createRow(2);
        for (int i = 0; i < headersBalance.length; i++) {
            Cell c = hRowBalance.createCell(i); c.setCellValue(headersBalance[i]); c.setCellStyle(hStyle);
        }
        int rowB = 3;
        for (CategorieComptable cat : planActif) {
            Double total = totalParPosteId.get(cat.getId());
            if (total == null || total == 0) continue;
            Row row = sheetBalance.createRow(rowB++);
            row.createCell(0).setCellValue(cat.getCode() != null ? cat.getCode() : "");
            row.createCell(1).setCellValue(cat.getLibelle() != null ? cat.getLibelle() : "");
            row.createCell(2).setCellValue(cat.getSens() != null ? cat.getSens() : "");
            row.createCell(3).setCellValue(total);
        }
        for (int i = 0; i < headersBalance.length; i++) sheetBalance.autoSizeColumn(i);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"rapports-financiers-" + annee + ".xlsx\"");
        wb.write(response.getOutputStream());
        wb.close();
    }
}
