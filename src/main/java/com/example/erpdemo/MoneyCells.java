package com.example.erpdemo;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * BigDecimal tablo hücreleri ve basit para formatları için yardımcılar.
 * Double'a çevirmeden, locale duyarlı formatlama yapar.
 */
public final class MoneyCells {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private MoneyCells() {}

    /** 2 ondalık ve binlik ayırıcılı TR sayı formatı (para benzeri). */
    public static NumberFormat number2TR() {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(true);
        return nf;
    }

    /** "123,45 TL" gibi metin döndürür (2 ondalık, binlik ayırıcılı). */
    public static String fmtTL(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        return number2TR().format(v) + " TL";
    }

    /**
     * BigDecimal kolonları için hazır hücre fabrikası.
     * Sağ hizalı ve 2 ondalık TR formatında yazar.
     */
    public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> twoDecimalsTR() {
        return col -> new TableCell<>() {
            private final NumberFormat nf = number2TR();
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(nf.format(v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        };
    }
}
