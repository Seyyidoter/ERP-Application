package com.example.erpdemo;

import javafx.fxml.FXML;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Onaylanmış talepleri PDF/Excel olarak üretir; dönem filtreleri desteklenir. */
public class ReportsController {

    /* ========================== Dönem Mantığı ========================== */

    private enum Period { TODAY, THIS_WEEK, THIS_MONTH, ROLLING_MONTH, ALL_TIME }

    /** UI – PDF */
    @FXML private void generateApprovedRequestsToday()        { generatePdfForPeriod(Period.TODAY); }
    @FXML private void generateApprovedRequestsThisWeek()     { generatePdfForPeriod(Period.THIS_WEEK); }
    @FXML private void generateApprovedRequestsThisMonth()    { generatePdfForPeriod(Period.THIS_MONTH); } // takvim ayı
    @FXML private void generateApprovedRequestsRollingMonth() { generatePdfForPeriod(Period.ROLLING_MONTH); } // son 1 ay
    @FXML private void generateApprovedRequestsAllTime()      { generatePdfForPeriod(Period.ALL_TIME); }
    @FXML private void generateApprovedRequestsReport()       { generatePdfForPeriod(Period.ALL_TIME); } // backward-compat

    /** UI – Excel */
    @FXML private void exportApprovedRequestsTodayExcel()        { generateExcelForPeriod(Period.TODAY); }
    @FXML private void exportApprovedRequestsThisWeekExcel()     { generateExcelForPeriod(Period.THIS_WEEK); }
    @FXML private void exportApprovedRequestsThisMonthExcel()    { generateExcelForPeriod(Period.THIS_MONTH); }
    @FXML private void exportApprovedRequestsRollingMonthExcel() { generateExcelForPeriod(Period.ROLLING_MONTH); }
    @FXML private void exportApprovedRequestsAllTimeExcel()      { generateExcelForPeriod(Period.ALL_TIME); }

    /* ========================== PDF ========================== */

    private void generatePdfForPeriod(Period period) {
        final var range = resolveRange(period); // [from, to)
        final LocalDate from = range.from();
        final LocalDate to   = range.to();

        Async.runVoid(() -> {
            try (PDDocument document = new PDDocument()) {
                PDFont font = loadFont(document);
                final String title = "Onaylanmış Talepler Raporu – " + periodTitle(period, from, to);

                final List<Request> approved;
                if (period == Period.ALL_TIME) {
                    approved = RequestDAO.getApprovedRequests();
                } else {
                    approved = RequestDAO.getApprovedRequestsBetween(from, to);
                }

                try (PdfWriter w = new PdfWriter(document, font)) {
                    w.startPage();
                    w.printlnWrapBlock(List.of(title, ""));

                    if (approved.isEmpty()) {
                        w.printlnWrapBlock(List.of("Seçilen dönem için onaylanmış talep bulunamadı.", ""));
                    } else {
                        Set<Integer> customerIds = new LinkedHashSet<>();
                        Set<Integer> requestIds  = new LinkedHashSet<>();
                        for (Request r : approved) { customerIds.add(r.getCustomerId()); requestIds.add(r.getId()); }

                        Map<Integer, String> nameMap  = CustomerDAO.getCustomerNamesByIds(customerIds);
                        Map<Integer, List<ItemRow>> itemsMap = fetchItemsForRequests(requestIds);

                        long       periodTotalQty  = 0;
                        BigDecimal periodTotalList = BigDecimal.ZERO;
                        BigDecimal periodTotalDisc = BigDecimal.ZERO;

                        for (Request r : approved) {
                            String cname   = nameMap.getOrDefault(r.getCustomerId(), "Bilinmiyor");
                            String dateStr = (r.getRequestDate() != null) ? r.getRequestDate().format(DF_DATE) : "";

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
                            int itemLines   = Math.max(items.size(), 1);
                            int footerLines = 4;
                            int blockLines  = headerBlock.size() + itemLines + footerLines + 1;
                            w.ensureSpaceFor(blockLines);

                            w.printlnRawBlock(headerBlock);

                            int        reqTotalQty  = 0;
                            BigDecimal reqTotalList = BigDecimal.ZERO;
                            BigDecimal reqTotalDisc = BigDecimal.ZERO;

                            if (items.isEmpty()) {
                                w.println("Kalem bulunamadı.");
                            } else {
                                for (ItemRow it : items) {
                                    BigDecimal subList = it.listPrice.multiply(BigDecimal.valueOf(it.quantity));
                                    BigDecimal subDisc = it.discountedPrice.multiply(BigDecimal.valueOf(it.quantity));

                                    reqTotalQty  += it.quantity;
                                    reqTotalList = reqTotalList.add(subList);
                                    reqTotalDisc = reqTotalDisc.add(subDisc);

                                    String line =
                                            padRight(trim(it.productName, COL_W_PRODUCT), COL_W_PRODUCT) + " " +
                                                    padLeft(String.valueOf(it.quantity), COL_W_QTY) + " " +
                                                    padLeft(fmtMoney(it.listPrice), COL_W_LIST) + " " +
                                                    padLeft(fmtMoney(it.discountedPrice), COL_W_DISC) + " " +
                                                    padLeft(fmtMoney(subDisc), COL_W_SUBTOTAL);
                                    w.println(line);
                                }
                            }

                            periodTotalQty  += reqTotalQty;
                            periodTotalList = periodTotalList.add(reqTotalList);
                            periodTotalDisc = periodTotalDisc.add(reqTotalDisc);

                            w.println(w.hrLineAscii());
                            w.println(String.format(LOCALE_TR, "Toplam Ürün Adedi   : %d", reqTotalQty));
                            w.println(String.format(LOCALE_TR, "Toplam Liste Tutarı : %s TL", fmtMoney(reqTotalList)));
                            w.println(String.format(LOCALE_TR, "Toplam İsk. Tutar   : %s TL", fmtMoney(reqTotalDisc)));
                            w.println("");
                            w.println("");
                        }

                        w.ensureSpaceFor(6);
                        w.println(w.hrLine());
                        w.println("Dönem Özeti");
                        w.println(w.hrLineAscii());
                        w.println(String.format(LOCALE_TR, "Genel Ürün Adedi    : %d", periodTotalQty));
                        w.println(String.format(LOCALE_TR, "Genel Liste Tutarı  : %s TL", fmtMoney(periodTotalList)));
                        w.println(String.format(LOCALE_TR, "Genel İsk. Tutarı   : %s TL", fmtMoney(periodTotalDisc)));
                    }
                }

                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", LOCALE_TR));
                Path outDir = Paths.get("reports");
                Files.createDirectories(outDir);
                String suffix = switch (period) {
                    case TODAY -> "BUGUN";
                    case THIS_WEEK -> "BU_HAFTA";
                    case THIS_MONTH -> "BU_AY";
                    case ROLLING_MONTH -> "SON_1_AY";
                    case ALL_TIME -> "TUM_ZAMANLAR";
                };
                Path outPath = outDir.resolve("SatisRaporu_" + suffix + "_" + ts + ".pdf");
                document.save(outPath.toFile());

                Async.later(() -> AppDialogs.info("PDF oluşturuldu: " + outPath.toAbsolutePath()));
            } catch (SQLException e) {
                Async.later(() -> AppDialogs.dbError("Rapor verilerini alma", e));
            } catch (IOException e) {
                Async.later(() -> AppDialogs.unexpectedError("PDF oluşturma", e));
            }
        }, null, null, null);
    }

    /* ========================== Excel ========================== */

    private void generateExcelForPeriod(Period period) {
        final var range = resolveRange(period);
        final LocalDate from = range.from();
        final LocalDate to   = range.to();

        Async.runVoid(() -> {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sh = wb.createSheet("Onaylanmış Talepler");

                // stiller
                CellStyle header = headerStyle(wb);
                CellStyle money  = moneyStyle(wb);
                CellStyle intCs  = integerStyle(wb);

                // YENİ: Talep ID için ortalı stiller
                CellStyle idStyle = wb.createCellStyle();
                idStyle.setAlignment(HorizontalAlignment.CENTER);

                CellStyle headerCenter = wb.createCellStyle();
                headerCenter.cloneStyleFrom(header);
                headerCenter.setAlignment(HorizontalAlignment.CENTER);

                int r = 0;
                Row titleRow = sh.createRow(r++);
                titleRow.createCell(0).setCellValue("Onaylanmış Talepler – " + periodTitle(period, from, to));
                r++;

                Row h = sh.createRow(r++);
                String[] cols = {
                        "Talep ID","Müşteri","Talep Tarihi","Durum",
                        "Ürün","Miktar","Liste Fiyatı","İskontolu Fiyat","Ara Toplam"
                };
                for (int i = 0; i < cols.length; i++) {
                    Cell c = h.createCell(i);
                    c.setCellValue(cols[i]);
                    c.setCellStyle(i == 0 ? headerCenter : header); // Talep ID başlığı ortalı
                }

                final List<Request> approved =
                        (period == Period.ALL_TIME)
                                ? RequestDAO.getApprovedRequests()
                                : RequestDAO.getApprovedRequestsBetween(from, to);

                if (!approved.isEmpty()) {
                    Set<Integer> customerIds = new LinkedHashSet<>();
                    Set<Integer> requestIds  = new LinkedHashSet<>();
                    for (Request rq : approved) { customerIds.add(rq.getCustomerId()); requestIds.add(rq.getId()); }
                    Map<Integer, String> nameMap  = CustomerDAO.getCustomerNamesByIds(customerIds);
                    Map<Integer, List<ItemRow>> itemsMap = fetchItemsForRequests(requestIds);

                    for (Request rq : approved) {
                        String cust = nameMap.getOrDefault(rq.getCustomerId(), "Bilinmiyor");
                        String date = rq.getRequestDate() == null ? "" : rq.getRequestDate().format(DF_DATE);

                        List<ItemRow> items = itemsMap.getOrDefault(rq.getId(), List.of());
                        if (items.isEmpty()) {
                            Row row = sh.createRow(r++);
                            int c = 0;
                            Cell id = row.createCell(c++); id.setCellValue(rq.getId()); id.setCellStyle(idStyle); // Talep ID ortalı
                            row.createCell(c++).setCellValue(cust);
                            row.createCell(c++).setCellValue(date);
                            row.createCell(c++).setCellValue(rq.getStatus());
                            row.createCell(c++).setCellValue("(kalem yok)");
                        } else {
                            for (ItemRow it : items) {
                                Row row = sh.createRow(r++);
                                int c = 0;
                                Cell id = row.createCell(c++); id.setCellValue(rq.getId()); id.setCellStyle(idStyle); // Talep ID ortalı
                                row.createCell(c++).setCellValue(cust);
                                row.createCell(c++).setCellValue(date);
                                row.createCell(c++).setCellValue(rq.getStatus());
                                row.createCell(c++).setCellValue(it.productName);
                                Cell qty = row.createCell(c++); qty.setCellValue(it.quantity); qty.setCellStyle(intCs);
                                Cell lp  = row.createCell(c++); lp.setCellValue(it.listPrice.doubleValue()); lp.setCellStyle(money);
                                Cell dp  = row.createCell(c++); dp.setCellValue(it.discountedPrice.doubleValue()); dp.setCellStyle(money);
                                Cell sub = row.createCell(c++); sub.setCellValue(it.discountedPrice.multiply(BigDecimal.valueOf(it.quantity)).doubleValue()); sub.setCellStyle(money);
                            }
                        }
                    }
                }

                for (int i = 0; i < 9; i++) {
                    sh.autoSizeColumn(i);
                    sh.setColumnWidth(i, Math.min(sh.getColumnWidth(i), 10000));
                }

                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", LOCALE_TR));
                Path outDir = Paths.get("reports");
                Files.createDirectories(outDir);
                String suffix = switch (period) {
                    case TODAY -> "BUGUN";
                    case THIS_WEEK -> "BU_HAFTA";
                    case THIS_MONTH -> "BU_AY";
                    case ROLLING_MONTH -> "SON_1_AY";
                    case ALL_TIME -> "TUM_ZAMANLAR";
                };
                Path outPath = outDir.resolve("SatisRaporu_" + suffix + "_" + ts + ".xlsx");
                try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) { wb.write(fos); }

                Async.later(() -> AppDialogs.info("Excel oluşturuldu: " + outPath.toAbsolutePath()));
            } catch (SQLException e) {
                Async.later(() -> AppDialogs.dbError("Rapor verilerini alma", e));
            } catch (IOException e) {
                Async.later(() -> AppDialogs.unexpectedError("Excel oluşturma", e));
            }
        }, null, null, null);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle cs = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true); cs.setFont(f);
        cs.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
        return cs;
    }
    private CellStyle moneyStyle(Workbook wb) {
        CellStyle cs = wb.createCellStyle();
        cs.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return cs;
    }
    private CellStyle integerStyle(Workbook wb) {
        CellStyle cs = wb.createCellStyle();
        cs.setDataFormat(wb.createDataFormat().getFormat("0"));
        return cs;
    }

    /* ========================== Ortaklar ========================== */

    private String periodTitle(Period p, LocalDate from, LocalDate to) {
        return switch (p) {
            case TODAY         -> "Bugün (" + LocalDate.now().format(DF_DATE) + ")";
            case THIS_WEEK     -> "Bu Hafta (" + from.format(DF_DATE) + " – " + to.minusDays(1).format(DF_DATE) + ")";
            case THIS_MONTH    -> "Bu Ay (" + from.format(DF_DATE) + " – " + to.minusDays(1).format(DF_DATE) + ")";
            case ROLLING_MONTH -> "Son 1 Ay (" + from.format(DF_DATE) + " – " + to.minusDays(1).format(DF_DATE) + ")";
            case ALL_TIME      -> "Tüm Zamanlar";
        };
    }

    /** [from, to) – 'to' her zaman exclusive */
    private DateRange resolveRange(Period p) {
        LocalDate today = LocalDate.now();
        return switch (p) {
            case TODAY -> new DateRange(today, today.plusDays(1));
            case THIS_WEEK -> {
                LocalDate from = today.with(java.time.DayOfWeek.MONDAY);
                if (from.isAfter(today)) from = from.minusWeeks(1);
                LocalDate to = from.plusWeeks(1);
                yield new DateRange(from, to);
            }
            case THIS_MONTH -> {
                LocalDate from = today.with(TemporalAdjusters.firstDayOfMonth());
                LocalDate to = from.plusMonths(1);
                yield new DateRange(from, to);
            }
            case ROLLING_MONTH -> {
                LocalDate from = today.minusMonths(1);
                LocalDate to   = today.plusDays(1);
                yield new DateRange(from, to);
            }
            case ALL_TIME -> new DateRange(null, null);
        };
    }

    /* ========================== PDF yazım altyapısı ========================== */

    private static final int COL_W_PRODUCT  = 32;
    private static final int COL_W_QTY      = 8;
    private static final int COL_W_LIST     = 12;
    private static final int COL_W_DISC     = 12;
    private static final int COL_W_SUBTOTAL = 12;

    private static final Locale LOCALE_TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DF_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", LOCALE_TR);

    /** Talep kalemlerini tek seferde çekip requestId'ye göre gruplar */
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
        return String.format(LOCALE_TR, "%.2f", v);
    }

    /* Küçük yardımcı tipi */
    private record DateRange(LocalDate from, LocalDate to) {}
}
