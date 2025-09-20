package com.example.erpdemo;

import javafx.fxml.FXML;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Onaylanmış talepleri PDF'e, sayfa taşırmadan çok sayfalı olarak yazar. */
public class ReportsController {

    @FXML
    private void generateApprovedRequestsReport() {
        try (PDDocument document = new PDDocument()) {

            PDType0Font font = loadFont(document);
            if (font == null) {
                AppDialogs.error("times.ttf bulunamadı.\nLütfen dosyayı resources/com/example/erpdemo/ altına koyun.");
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
                    DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

                    // müşteri adlarını toplu çek
                    Set<Integer> customerIds = new LinkedHashSet<>();
                    for (Request r : approved) customerIds.add(r.getCustomerId());
                    Map<Integer, String> nameMap = CustomerDAO.getCustomerNamesByIds(customerIds);

                    for (Request r : approved) {
                        String cname = nameMap.getOrDefault(r.getCustomerId(), "Bilinmiyor");
                        String dateStr = (r.getRequestDate() != null) ? r.getRequestDate().format(dateFmt) : "";

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
                        BigDecimal totalList = BigDecimal.ZERO;
                        BigDecimal totalDisc = BigDecimal.ZERO;

                        for (ItemRow it : items) {
                            BigDecimal subList = it.listPrice.multiply(BigDecimal.valueOf(it.quantity));
                            BigDecimal subDisc = it.discountedPrice.multiply(BigDecimal.valueOf(it.quantity));

                            totalQty += it.quantity;
                            totalList = totalList.add(subList);
                            totalDisc = totalDisc.add(subDisc);

                            String line = String.format("%-28s %6d %12.2f %12.2f %12.2f",
                                    trim(it.productName, 28),
                                    it.quantity,
                                    it.listPrice.setScale(2, RoundingMode.HALF_UP).doubleValue(),
                                    it.discountedPrice.setScale(2, RoundingMode.HALF_UP).doubleValue(),
                                    subDisc.setScale(2, RoundingMode.HALF_UP).doubleValue());
                            w.println(line);
                        }

                        w.println("----------------------------------------------------------------------");
                        w.println(String.format("Toplam Ürün Adedi: %d", totalQty));
                        w.println(String.format("Toplam Liste Tutarı: %.2f TL",
                                totalList.setScale(2, RoundingMode.HALF_UP).doubleValue()));
                        w.println(String.format("Toplam İskontolu Tutar: %.2f TL",
                                totalDisc.setScale(2, RoundingMode.HALF_UP).doubleValue()));
                        w.println("");
                    }
                }
            }

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
            Path outDir = Paths.get("reports");
            Files.createDirectories(outDir);
            Path outPath = outDir.resolve("ApprovedRequestsReport_" + ts + ".pdf");
            document.save(outPath.toFile());

            AppDialogs.info("Rapor oluşturuldu: " + outPath.toAbsolutePath());

        } catch (SQLException e) {
            AppDialogs.dbError("Rapor verilerini alma", e);
        } catch (IOException e) {
            AppDialogs.unexpectedError("PDF oluşturma", e);
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
                            rs.getBigDecimal("ListeFiyati"),
                            rs.getBigDecimal("IskontoluFiyat")
                    ));
                }
            }
        } catch (SQLException ignore) { }
        return list;
    }

    /* -------------------- Font yükleme -------------------- */

    private PDType0Font loadFont(PDDocument doc) throws IOException {
        URL url = ReportsController.class.getResource("/com/example/erpdemo/times.ttf");
        if (url != null) try (InputStream in = url.openStream()) { return PDType0Font.load(doc, in); }
        try (InputStream in = ReportsController.class.getResourceAsStream("/times.ttf")) {
            if (in != null) return PDType0Font.load(doc, in);
        }
        return null;
    }

    /* -------------------- İç modeller -------------------- */

    private static final class ItemRow {
        final String productName; final int quantity;
        final BigDecimal listPrice; final BigDecimal discountedPrice;
        ItemRow(String productName, int quantity, BigDecimal listPrice, BigDecimal discountedPrice) {
            this.productName = productName; this.quantity = quantity;
            this.listPrice = listPrice == null ? BigDecimal.ZERO : listPrice;
            this.discountedPrice = discountedPrice == null ? BigDecimal.ZERO : discountedPrice;
        }
    }

    /* -------------------- PDF yardımcıları -------------------- */

    /** Basit satır-yazıcı: font, satır yüksekliği ve sayfa taşması yönetimi. */
    private static final class PdfWriter implements AutoCloseable {
        private final PDDocument doc;
        private final PDType0Font font;
        private PDPageContentStream cs;

        // metrikler
        private float leading      = 14.5f;
        private float marginLeft   = 25f;
        private float startY       = 750f;
        private float cursorY      = startY;
        private final float bottomMargin = 40f;

        PdfWriter(PDDocument doc, PDType0Font font) {
            this.doc = doc;
            this.font = font;
        }

        void startPage() throws IOException {
            if (cs != null) { cs.endText(); cs.close(); }
            PDPage page = new PDPage();
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
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
            if (cursorY - (lines * leading) < bottomMargin) {
                startPage();
            }
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
