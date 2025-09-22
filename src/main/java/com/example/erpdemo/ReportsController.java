package com.example.erpdemo;

import javafx.fxml.FXML;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

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

    private static final int COL_W_PRODUCT  = 32;
    private static final int COL_W_QTY      = 8;
    private static final int COL_W_LIST     = 12;
    private static final int COL_W_DISC     = 12;
    private static final int COL_W_SUBTOTAL = 12;

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    @FXML
    private void generateApprovedRequestsReport() {
        Async.runVoid(() -> {
            try (PDDocument document = new PDDocument()) {
                PDFont font = loadFont(document);
                try (PdfWriter w = new PdfWriter(document, font)) {
                    w.startPage();
                    w.printlnWrapBlock(List.of("Onaylanmış Talepler Raporu", ""));

                    var approved = RequestDAO.getApprovedRequests();
                    if (approved.isEmpty()) {
                        w.printlnWrapBlock(List.of("Onaylanmış talep bulunamadı.", ""));
                    } else {
                        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", TR);

                        Set<Integer> customerIds = new LinkedHashSet<>();
                        Set<Integer> requestIds  = new LinkedHashSet<>();
                        for (Request r : approved) {
                            customerIds.add(r.getCustomerId());
                            requestIds.add(r.getId());
                        }
                        Map<Integer, String> nameMap  = CustomerDAO.getCustomerNamesByIds(customerIds);
                        Map<Integer, List<ItemRow>> itemsMap = fetchItemsForRequests(requestIds);

                        for (Request r : approved) {
                            String cname  = nameMap.getOrDefault(r.getCustomerId(), "Bilinmiyor");
                            String dateStr = r.getRequestDate() != null ? r.getRequestDate().format(dateFmt) : "";

                            List<String> headerBlock = new ArrayList<>();
                            headerBlock.add(w.hrLine());
                            headerBlock.add("Talep ID      : " + r.getId());
                            headerBlock.addAll(w.kvLines("Müşteri Adı   : ", cname));
                            headerBlock.add("Talep Tarihi  : " + dateStr);
                            headerBlock.add("Durum         : " + r.getStatus());
                            headerBlock.add(w.hrLine());
                            headerBlock.add(
                                    padRight("Ürün", COL_W_PRODUCT) + " " +
                                            padLeft("Miktar", COL_W_QTY) + " " +
                                            padLeft("Liste F.", COL_W_LIST) + " " +
                                            padLeft("İsk. Fiyat", COL_W_DISC) + " " +
                                            padLeft("Ara Toplam", COL_W_SUBTOTAL));
                            headerBlock.add(w.hrLineAscii());

                            List<ItemRow> items = itemsMap.getOrDefault(r.getId(), List.of());
                            int itemLines = Math.max(items.size(), 1);
                            int footerLines = 4;
                            int blockLines = headerBlock.size() + itemLines + footerLines + 1;
                            w.ensureSpaceFor(blockLines);

                            w.printlnRawBlock(headerBlock);

                            int totalQty = 0;
                            BigDecimal totalList = BigDecimal.ZERO;
                            BigDecimal totalDisc = BigDecimal.ZERO;

                            if (items.isEmpty()) {
                                w.println("Kalem bulunamadı.");
                            } else {
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
                            }

                            w.println(w.hrLineAscii());
                            w.println(String.format(TR, "Toplam Ürün Adedi   : %d", totalQty));
                            w.println(String.format(TR, "Toplam Liste Tutarı : %s TL", fmtMoney(totalList)));
                            w.println(String.format(TR, "Toplam İsk. Tutar   : %s TL", fmtMoney(totalDisc)));
                            w.println("");
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

    /** Tüm talep kalemlerini tek seferde çekip requestId'ye göre gruplar */
    private Map<Integer, List<ItemRow>> fetchItemsForRequests(Collection<Integer> requestIds) throws SQLException {
        if (requestIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(requestIds.size(), "?"));
        String sql = """
            SELECT tk.TalepId, s.UrunAdi, tk.Miktar, s.Fiyat AS ListeFiyati, tk.TeklifFiyati AS IskontoluFiyat
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Stoklar s ON s.Id = tk.UrunId
            WHERE tk.TalepId IN (""" + placeholders + ") ORDER BY tk.TalepId, tk.Id";

        Map<Integer, List<ItemRow>> map = new LinkedHashMap<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Integer id : requestIds) ps.setInt(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int rid = rs.getInt("TalepId");
                    map.computeIfAbsent(rid, k -> new ArrayList<>()).add(
                            new ItemRow(
                                    rs.getString("UrunAdi"),
                                    rs.getInt("Miktar"),
                                    rs.getBigDecimal("ListeFiyati"),
                                    rs.getBigDecimal("IskontoluFiyat")
                            )
                    );
                }
            }
        }
        return map;
    }

    private PDFont loadFont(PDDocument doc) throws IOException {
        PDFont f;
        if ((f = tryLoadTtf(doc, "/com/example/erpdemo/DejaVuSansMono.ttf")) != null) return f;
        if ((f = tryLoadTtf(doc, "/DejaVuSansMono.ttf")) != null) return f;
        if ((f = tryLoadTtf(doc, "/com/example/erpdemo/DejaVuSans.ttf")) != null) return f;
        if ((f = tryLoadTtf(doc, "/DejaVuSans.ttf")) != null) return f;
        return new PDType1Font(Standard14Fonts.FontName.COURIER);
    }
    private PDFont tryLoadTtf(PDDocument doc, String cpPath) {
        try {
            URL url = ReportsController.class.getResource(cpPath);
            if (url == null) return null;
            try (InputStream in = url.openStream()) {
                return PDType0Font.load(doc, in, true);
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static final class ItemRow {
        final String productName; final int quantity;
        final BigDecimal listPrice; final BigDecimal discountedPrice;
        ItemRow(String productName, int quantity, BigDecimal listPrice, BigDecimal discountedPrice) {
            this.productName = productName;
            this.quantity = quantity;
            this.listPrice = listPrice == null ? BigDecimal.ZERO : listPrice;
            this.discountedPrice = discountedPrice == null ? BigDecimal.ZERO : discountedPrice;
        }
    }

    /** PDF yazımını kolaylaştıran yardımcı sınıf */
    private static final class PdfWriter implements AutoCloseable {
        private final PDDocument doc;
        private final PDFont font;
        private PDPageContentStream cs;
        private float leading = 14.5f;
        private float marginLeft = 25f;
        private float marginRight = 25f;
        private float fontSize = 12f;
        private float usableWidth;
        private float startY = 750f;
        private float cursorY = startY;
        private final float bottomMargin = 40f;
        private String lineChar = "─";

        PdfWriter(PDDocument doc, PDFont font) { this.doc = doc; this.font = font; }

        void startPage() throws IOException {
            if (cs != null) { cs.endText(); cs.close(); }
            PDPage page = new PDPage();
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.setLeading(leading);
            cs.newLineAtOffset(marginLeft, startY);
            cursorY = startY;

            float pageWidth = page.getMediaBox().getWidth();
            usableWidth = pageWidth - marginLeft - marginRight;

            if (!canDisplay(lineChar)) lineChar = "-";
        }

        void printlnRawBlock(List<String> lines) throws IOException {
            ensureSpaceFor(lines.size());
            for (String s : lines) {
                cs.showText(s == null ? "" : s);
                cs.newLine();
                cursorY -= leading;
            }
        }

        void println(String text) throws IOException {
            ensureSpaceFor(1);
            cs.showText(text == null ? "" : text);
            cs.newLine();
            cursorY -= leading;
        }

        void printlnWrapBlock(List<String> texts) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String t : texts) lines.addAll(wrapToWidth(t, usableWidth));
            printlnRawBlock(lines);
        }

        List<String> kvLines(String label, String value) throws IOException {
            if (label == null) label = "";
            if (value == null) value = "";
            float labelW = textWidth(label);
            float wrapWidth = Math.max(usableWidth - labelW, usableWidth * 0.5f);
            List<String> parts = wrapToWidth(value, wrapWidth);

            List<String> lines = new ArrayList<>();
            if (parts.isEmpty()) {
                lines.add(label);
                return lines;
            }
            lines.add(label + parts.get(0));
            if (parts.size() > 1) {
                String indent = spacesForWidth(labelW);
                for (int i = 1; i < parts.size(); i++) {
                    lines.add(indent + parts.get(i));
                }
            }
            return lines;
        }

        String hrLine() throws IOException {
            float charW = Math.max(textWidth(lineChar), 1f);
            int count = Math.max(40, (int) (usableWidth / charW));
            return lineChar.repeat(Math.min(count, 180));
        }
        String hrLineAscii() throws IOException {
            float charW = Math.max(textWidth("-"), 1f);
            int count = Math.max(40, (int) (usableWidth / charW));
            return "-".repeat(Math.min(count, 180));
        }

        void ensureSpaceFor(int lines) throws IOException {
            if (cursorY - (lines * leading) < bottomMargin) startPage();
        }

        private float textWidth(String s) throws IOException {
            if (s == null || s.isEmpty()) return 0f;
            return font.getStringWidth(s) / 1000f * fontSize;
        }

        private List<String> wrapToWidth(String text, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null) { lines.add(""); return lines; }

            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                String candidate = current.isEmpty() ? w : current + " " + w;
                if (textWidth(candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    if (textWidth(w) <= maxWidth) {
                        current.append(w);
                    } else {
                        int start = 0;
                        while (start < w.length()) {
                            int end = w.length();
                            while (end > start && textWidth(w.substring(start, end)) > maxWidth) end--;
                            if (end == start) end = Math.min(start + 1, w.length());
                            lines.add(w.substring(start, end));
                            start = end;
                        }
                    }
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            if (lines.isEmpty()) lines.add("");
            return lines;
        }

        private String spacesForWidth(float width) throws IOException {
            float spaceW = Math.max(textWidth(" "), 1f);
            int count = Math.max(0, (int) (width / spaceW));
            return " ".repeat(count);
        }

        private boolean canDisplay(String ch) {
            int cp = ch.codePointAt(0);
            try {
                if (font instanceof PDType0Font) {
                    return ((PDType0Font) font).hasGlyph(cp);
                }
                return cp < 0x80;
            } catch (Exception ignore) {
                return false;
            }
        }

        @Override public void close() throws IOException {
            if (cs != null) { cs.endText(); cs.close(); }
        }
    }

    private static String trim(String s, int max) { if (s == null) return ""; return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    private static String padRight(String s, int width) { if (s == null) s = ""; return s.length() >= width ? s : s + " ".repeat(width - s.length()); }
    private static String padLeft(String s, int width) { if (s == null) s = ""; return s.length() >= width ? s : " ".repeat(width - s.length()) + s; }
    private static String fmtMoney(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        v = v.setScale(2, RoundingMode.HALF_UP);
        return String.format(TR, "%.2f", v);
    }
}
