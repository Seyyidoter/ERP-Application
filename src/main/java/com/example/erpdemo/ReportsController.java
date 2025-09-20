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

    // --- Kolon genişlikleri (monospace ile hizalanır) ---
    private static final int COL_W_PRODUCT  = 32;
    private static final int COL_W_QTY      = 8;
    private static final int COL_W_LIST     = 12;
    private static final int COL_W_DISC     = 12;
    private static final int COL_W_SUBTOTAL = 12;

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    @FXML
    private void generateApprovedRequestsReport() {
        // Ağır iş: DB + PDF → FX thread’ini bloklamayalım
        Async.runVoid(() -> {
            try (PDDocument document = new PDDocument()) {

                PDType0Font font = loadPreferredFont(document);
                if (font == null) {
                    throw new IOException("""
                        PDF yazı tipi bulunamadı.
                        Lütfen 'resources/com/example/erpdemo/DejaVuSansMono.ttf'
                        (veya classpath kökünde '/DejaVuSansMono.ttf') ekleyin.
                        """);
                }

                try (PdfWriter w = new PdfWriter(document, font)) {
                    w.startPage();
                    w.println("Onaylanmış Talepler Raporu");
                    w.println("");

                    var approved = RequestDAO.getApprovedRequests();
                    if (approved.isEmpty()) {
                        w.println("Onaylanmış talep bulunamadı.");
                    } else {
                        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", TR);

                        // müşteri adlarını toplu çek (tek sefer)
                        Set<Integer> customerIds = new LinkedHashSet<>();
                        for (Request r : approved) customerIds.add(r.getCustomerId());
                        Map<Integer, String> nameMap = CustomerDAO.getCustomerNamesByIds(customerIds);

                        for (Request r : approved) {
                            String cname = nameMap.getOrDefault(r.getCustomerId(), "Bilinmiyor");
                            String dateStr = (r.getRequestDate() != null) ? r.getRequestDate().format(dateFmt) : "";

                            w.println("──────────────────────────────────────────────────────────────────────────");
                            w.println("Talep ID      : " + r.getId());
                            w.println("Müşteri Adı   : " + cname);
                            w.println("Talep Tarihi  : " + dateStr);
                            w.println("Durum         : " + r.getStatus());
                            w.println("──────────────────────────────────────────────────────────────────────────");

                            // HATA YUTMAK YOK — SQLException dışarı fırlayacak
                            List<ItemRow> items = fetchItemsForRequest(r.getId());
                            if (items.isEmpty()) {
                                w.println("Kalem bulunamadı.");
                                w.println("");
                                continue;
                            }

                            // Başlık satırı
                            w.println(
                                    padRight("Ürün", COL_W_PRODUCT) + " " +
                                            padLeft("Miktar", COL_W_QTY) + " " +
                                            padLeft("Liste F.", COL_W_LIST) + " " +
                                            padLeft("İsk. Fiyat", COL_W_DISC) + " " +
                                            padLeft("Ara Toplam", COL_W_SUBTOTAL)
                            );
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

                                String line =
                                        padRight(trim(it.productName, COL_W_PRODUCT), COL_W_PRODUCT) + " " +
                                                padLeft(String.valueOf(it.quantity), COL_W_QTY) + " " +
                                                padLeft(fmtMoney(it.listPrice), COL_W_LIST) + " " +
                                                padLeft(fmtMoney(it.discountedPrice), COL_W_DISC) + " " +
                                                padLeft(fmtMoney(subDisc), COL_W_SUBTOTAL);

                                w.println(line);
                            }

                            w.println("----------------------------------------------------------------------");
                            w.println(String.format(TR, "Toplam Ürün Adedi   : %d", totalQty));
                            w.println(String.format(TR, "Toplam Liste Tutarı : %s TL", fmtMoney(totalList)));
                            w.println(String.format(TR, "Toplam İsk. Tutar   : %s TL", fmtMoney(totalDisc)));
                            w.println("");
                        }
                    }
                }

                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", TR));
                Path outDir = Paths.get("reports");
                Files.createDirectories(outDir);
                Path outPath = outDir.resolve("ApprovedRequestsReport_" + ts + ".pdf");
                document.save(outPath.toFile());

                Async.later(() -> AppDialogs.info("Rapor oluşturuldu: " + outPath.toAbsolutePath()));

            } catch (SQLException e) {
                Async.later(() -> AppDialogs.dbError("Rapor verilerini alma", e));
            } catch (IOException e) {
                Async.later(() -> AppDialogs.unexpectedError("PDF oluşturma", e));
            }
        }, null, null, null);
    }

    /* -------------------- DB yardımcıları -------------------- */

    // >>> DEĞİŞTİ: SQLException'ı yutmak yerine dışarı atıyoruz.
    private List<ItemRow> fetchItemsForRequest(int requestId) throws SQLException {
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
        }
        return list;
    }

    /* -------------------- Font yükleme -------------------- */
    /** Sadece DejaVuSansMono.ttf yükler. Bulamazsa null döner. */
    private PDType0Font loadPreferredFont(PDDocument doc) throws IOException {
        // 1) resources/com/example/erpdemo/DejaVuSansMono.ttf
        PDType0Font mono = tryLoadFont(doc, "/com/example/erpdemo/DejaVuSansMono.ttf");
        if (mono != null) return mono;
        // 2) classpath kökü /DejaVuSansMono.ttf
        return tryLoadFont(doc, "/DejaVuSansMono.ttf");
    }

    private PDType0Font tryLoadFont(PDDocument doc, String path) throws IOException {
        URL url = ReportsController.class.getResource(path);
        if (url != null) {
            try (InputStream in = url.openStream()) {
                return PDType0Font.load(doc, in, true);
            } catch (Exception ignore) {
                // yut – bir sonrakini dene
            }
        }
        return null;
    }

    /* -------------------- İç modeller -------------------- */
    private static final class ItemRow {
        final String productName; final int quantity;
        final BigDecimal listPrice; final BigDecimal discountedPrice;
        ItemRow(String productName, int quantity, BigDecimal listPrice, BigDecimal discountedPrice) {
            this.productName = productName;
            this.quantity = quantity;
            this.listPrice = (listPrice == null ? BigDecimal.ZERO : listPrice);
            this.discountedPrice = (discountedPrice == null ? BigDecimal.ZERO : discountedPrice);
        }
    }

    /* -------------------- PDF yardımcıları -------------------- */
    private static final class PdfWriter implements AutoCloseable {
        private final PDDocument doc;
        private final PDType0Font font;
        private PDPageContentStream cs;
        private float leading = 14.5f;
        private float marginLeft = 25f;
        private float startY = 750f;
        private float cursorY = startY;
        private final float bottomMargin = 40f;

        PdfWriter(PDDocument doc, PDType0Font font) { this.doc = doc; this.font = font; }

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

    // --- küçük yardımcılar ---
    private static String trim(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    private static String padRight(String s, int width) { if (s == null) s = ""; return s.length() >= width ? s : s + " ".repeat(width - s.length()); }
    private static String padLeft(String s, int width) { if (s == null) s = ""; return s.length() >= width ? s : " ".repeat(width - s.length()) + s; }
    private static String fmtMoney(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        v = v.setScale(2, RoundingMode.HALF_UP);
        return String.format(TR, "%.2f", v);
    }
}
