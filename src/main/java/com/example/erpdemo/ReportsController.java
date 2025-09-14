package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReportsController {

    @FXML
    private void generateApprovedRequestsReport() {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                // Font yükle
                PDType0Font font = loadFont(document);
                if (font == null) {
                    showInfo("Hata", "times.ttf bulunamadı (assets klasörüne koyun).");
                    return;
                }

                cs.beginText();
                cs.setFont(font, 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(25, 750);

                cs.showText("Onaylanmış Talepler Raporu"); cs.newLine();
                cs.setFont(font, 10);

                var rows = RequestDAO.getApprovedRequests();
                if (rows.isEmpty()) {
                    cs.showText("Onaylanmış talep bulunamadı.");
                } else {
                    for (Request r : rows) {
                        Customer c = CustomerDAO.getCustomerById(r.getCustomerId());
                        String cname = (c != null) ? c.getCompanyName() : "Bilinmiyor";

                        cs.showText("--------------------------------------------------------------------------"); cs.newLine();
                        cs.showText("Talep ID: "     + r.getId());            cs.newLine();
                        cs.showText("Müşteri Adı: "  + cname);               cs.newLine();
                        cs.showText("Talep Tarihi: " + r.getRequestDate());  cs.newLine();
                        cs.showText("Durum: "        + r.getStatus());       cs.newLine();
                        cs.showText("--------------------------------------------------------------------------"); cs.newLine();
                    }
                }
                cs.endText();
            }

            // ======= ZAMAN DAMGALI DOSYA ADI + reports/ klasörü =======
            // Windows uyumu için saat kısmında ':' yerine '.' kullanıyoruz
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
            String fileName = "ApprovedRequestsReport_" + ts + ".pdf";

            Path outDir = Paths.get("reports");
            Files.createDirectories(outDir); // yoksa oluştur
            Path outPath = outDir.resolve(fileName);

            document.save(outPath.toFile());
            showInfo("Başarılı", "Rapor oluşturuldu: " + outPath.toAbsolutePath());

        } catch (IOException | SQLException e) {
            showInfo("Hata", "PDF oluşturulamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** times.ttf için sağlam yükleyici: önce assets/, sonra paket kökü */
    private PDType0Font loadFont(PDDocument doc) throws IOException {
        // 1) /com/example/erpdemo/assets/times.ttf (önerilen yer)
        URL abs1 = ReportsController.class.getResource("/com/example/erpdemo/assets/times.ttf");
        if (abs1 != null) {
            try (InputStream in = abs1.openStream()) { return PDType0Font.load(doc, in); }
        }
        // 2) /com/example/erpdemo/times.ttf (senin mevcut diziliminde varsa)
        URL abs2 = ReportsController.class.getResource("/com/example/erpdemo/times.ttf");
        if (abs2 != null) {
            try (InputStream in = abs2.openStream()) { return PDType0Font.load(doc, in); }
        }
        // 3) Paket göreli (assets altı)
        try (InputStream in = ReportsController.class.getResourceAsStream("assets/times.ttf")) {
            if (in != null) return PDType0Font.load(doc, in);
        }
        return null; // bulunamadı
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
