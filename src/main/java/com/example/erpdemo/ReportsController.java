package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;

public class ReportsController {

    @FXML
    private void generateApprovedRequestsReport() {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                // Font (resources/com/example/erpdemo/assets/times.ttf)
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

                ObservableList<Request> rows = RequestDAO.getApprovedRequests();
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

            String file = "ApprovedRequestsReport.pdf";
            document.save(file);
            showInfo("Başarılı", "Rapor oluşturuldu: " + file);

        } catch (IOException | SQLException e) {
            showInfo("Hata", "PDF oluşturulamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** assets/times.ttf için sağlam yükleyici. */
    private PDType0Font loadFont(PDDocument doc) throws IOException {
        // 1) Mutlak classpath
        URL abs = ReportsController.class.getResource("/com/example/erpdemo/assets/times.ttf");
        if (abs != null) {
            try (InputStream in = abs.openStream()) { return PDType0Font.load(doc, in); }
        }
        // 2) Paket göreli
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
