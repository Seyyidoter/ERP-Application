package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Onaylanmış talepleri PDF'e, sayfa taşırmadan çok sayfalı olarak yazar. */
public class ReportsController {

    @FXML
    private void generateApprovedRequestsReport() {
        try (PDDocument document = new PDDocument()) {

            PDType0Font font = loadFont(document);
            if (font == null) {
                showInfo("Hata",
                        "times.ttf bulunamadı.\n" +
                                "Lütfen dosyayı resources/com/example/erpdemo/ altına koyun.");
                return;
            }

            try (PdfWriter w = new PdfWriter(document, font)) {
                w.startPage();
                w.println("Onaylanmış Talepler Raporu");
                w.println("");

                var approved = RequestDAO.getApprovedRequests();
                if (approved.isEmpty()) {
                    w.println("Onaylanmış talep bulunamadı.");
                } else {
                    // SADECE BU KISIM DEĞİŞTİ: Türkiye formatı -> dd.MM.yyyy
                    DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

                    for (Request r : approved) {
                        String cname = "Bilinmiyor";
                        try {
                            Customer c = CustomerDAO.getCustomerById(r.getCustomerId());
                            if (c != null) cname = c.getCompanyName();
                        } catch (SQLException ignore) {}

                        String dateStr = (r.getRequestDate() != null)
                                ? r.getRequestDate().format(dateFmt)
                                : "";

                        w.println("--------------------------------------------------------------------------");
                        w.println("Talep ID: " + r.getId());
                        w.println("Müşteri Adı: " + cname);
                        w.println("Talep Tarihi: " + dateStr);
                        w.println("Durum: " + r.getStatus());
                        w.println("--------------------------------------------------------------------------");

                        List<ItemRow> items = fetchItemsForRequest(r.getId());
                        if (items.isEmpty()) {
                            w.println("Kalem bulunamadı.");
                            w.println("");
                            continue;
                        }

                        w.println("Ürün                         Miktar    Liste F.    İsk. Fiyat   Ara Toplam");
                        w.println("----------------------------------------------------------------------");

                        int totalQty = 0;
                        double totalList = 0;
                        double totalDisc = 0;

                        for (ItemRow it : items) {
                            double subList = it.quantity * it.listPrice;
                            double subDisc = it.quantity * it.discountedPrice;

                            totalQty += it.quantity;
                            totalList += subList;
                            totalDisc += subDisc;

                            String line = String.format("%-28s %6d %12.2f %12.2f %12.2f",
                                    trim(it.productName, 28),
                                    it.quantity,
                                    it.listPrice,
                                    it.discountedPrice,
                                    subDisc);
                            w.println(line);
                        }

                        w.println("----------------------------------------------------------------------");
                        w.println(String.format("Toplam Ürün Adedi: %d", totalQty));
                        w.println(String.format("Toplam Liste Tutarı: %.2f TL", totalList));
                        w.println(String.format("Toplam İskontolu Tutar: %.2f TL", totalDisc));
                        w.println("");
                    }
                }
            }

            // Çalıştırma klasörünün altına reports/ ve benzersiz dosya adı
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
            Path outDir = Paths.get("reports");
            Files.createDirectories(outDir);
            Path outPath = outDir.resolve("ApprovedRequestsReport_" + ts + ".pdf");
            document.save(outPath.toFile());

            showInfo("Başarılı", "Rapor oluşturuldu: " + outPath.toAbsolutePath());

        } catch (IOException | SQLException e) {
            showInfo("Hata", "PDF oluşturulamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* -------------------- DB yardımcıları -------------------- */

    private List<ItemRow> fetchItemsForRequest(int requestId) {
        String sql = """
            SELECT s.UrunAdi, tk.Miktar, s.Fiyat AS ListeFiyati, tk.TeklifFiyati AS IskontoluFiyat
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Stoklar s ON s.Id = tk.UrunId
            WHERE tk.TalepId = ?
            ORDER BY tk.Id
            """;
        List<ItemRow> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ItemRow(
                            rs.getString("UrunAdi"),
                            rs.getInt("Miktar"),
                            rs.getDouble("ListeFiyati"),
                            rs.getDouble("IskontoluFiyat")
                    ));
                }
            }
        } catch (SQLException ignore) {}
        return list;
    }

    /* -------------------- Font yükleme -------------------- */

    private PDType0Font loadFont(PDDocument doc) throws IOException {
        // Önce canonical path
        URL url = ReportsController.class.getResource("/com/example/erpdemo/times.ttf");
        if (url != null) try (InputStream in = url.openStream()) { return PDType0Font.load(doc, in); }
        // Alternatif kök
        try (InputStream in = ReportsController.class.getResourceAsStream("/times.ttf")) {
            if (in != null) return PDType0Font.load(doc, in);
        }
        return null;
    }

    /* -------------------- UI yardımcıları -------------------- */

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }

    /* -------------------- İç sınıflar -------------------- */

    /** Basit satır modeli. */
    private static final class ItemRow {
        final String productName; final int quantity; final double listPrice; final double discountedPrice;
        ItemRow(String productName, int quantity, double listPrice, double discountedPrice) {
            this.productName = productName; this.quantity = quantity; this.listPrice = listPrice; this.discountedPrice = discountedPrice;
        }
    }

    /** Çok satırlı metin yazımı ve sayfa taşırma için minimal yardımcı. */
    private static final class PdfWriter implements AutoCloseable {
        private final PDDocument doc;
        private final PDType0Font font;
        private org.apache.pdfbox.pdmodel.PDPageContentStream cs;
        private float leading = 14.5f;
        private float marginLeft = 25f;
        private float startY = 750f;
        private float cursorY = startY;
        private final float bottomMargin = 40f;

        PdfWriter(PDDocument doc, PDType0Font font) { this.doc = doc; this.font = font; }

        void startPage() throws IOException {
            if (cs != null) { cs.endText(); cs.close(); }
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(font, 12);
            cs.setLeading(leading);
            cs.newLineAtOffset(marginLeft, startY);
            cursorY = startY;
        }

        void println(String text) throws IOException {
            ensureSpace(1);
            cs.showText(text == null ? "" : text);
            cs.newLine();
            cursorY -= leading;
        }

        private void ensureSpace(int lines) throws IOException {
            if (cursorY - (lines * leading) < bottomMargin) startPage();
        }

        @Override public void close() throws IOException {
            if (cs != null) { cs.endText(); cs.close(); }
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
