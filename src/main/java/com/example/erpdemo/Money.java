package com.example.erpdemo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/** TL için tek noktadan format/parsing + yuvarlama yardımcıları. */
public final class Money {
    private static final Locale TR = new Locale("tr", "TR");

    private Money() {}

    /** 2 ondalık HALF_UP ölçekleme. null -> 0.00 */
    public static BigDecimal scale2(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 2 ondalık, 1.234,56 biçiminde (para sembolsüz). null -> "0,00" */
    public static String fmtTR(BigDecimal value) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(value == null ? BigDecimal.ZERO : value);
    }

    /** 2 ondalık, TL sembollü (“₺1.234,56” ya da yerelle eşdeğer). */
    public static String fmtTRWithSymbol(BigDecimal value) {
        NumberFormat cf = NumberFormat.getCurrencyInstance(TR);
        return cf.format(value == null ? BigDecimal.ZERO : value);
    }

    /** Kullanıcı girişini (1.234,56 / 1234.56 vb.) BigDecimal’a parse eder. */
    public static BigDecimal parseTR(String text) throws ParseException {
        if (text == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String s = text.trim();
        if (s.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 1) Para sembolleri ve boşlukları temizle
        s = s.replace("₺", "")
                .replace("TL", "")
                .replaceAll("\\s+", "");

        int lastComma = s.lastIndexOf(',');
        int lastDot   = s.lastIndexOf('.');

        // Hem virgül hem nokta varsa → sonuncusu ondalık, diğeri binliktir
        if (lastComma != -1 && lastDot != -1) {
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (lastComma != -1) {
            // Sadece virgül varsa → ondalık kabul et
            s = s.replace(',', '.');
        } else {
            // Hiçbiri yoksa veya sadece nokta varsa → olduğu gibi bırak
        }
        if (!s.matches("[0-9.]+")) {
            throw new ParseException("Geçersiz sayı biçimi: " + text, 0);
        }
        // 4) Double yerine BigDecimal oluştur
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new ParseException("Sayı ayrıştırılamadı: " + text, 0);
        }
    }
}
