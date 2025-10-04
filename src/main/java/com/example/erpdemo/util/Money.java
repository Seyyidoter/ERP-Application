package com.example.erpdemo.util;

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
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setParseIntegerOnly(false);
        Number n = nf.parse(text.trim());
        return new BigDecimal(n.toString());
    }
}
