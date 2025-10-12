package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Yeni/Düzenle Ürün dialogu — asenkron kaydetme ile UI donmasını engeller. */
public class NewProductController {

    @FXML private Label     titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private TextField unitField;

    private Stage   dialogStage;
    private Product product;

    public void setDialogStage(Stage dialogStage) { this.dialogStage = dialogStage; }

    @FXML
    public void initialize() {
        // Fiyat: TextFormatter yok — TR (1.234,56) gibi girişleri Money.parseTR çözer.
        // priceField.setTextFormatter(new TextFormatter<>(numericDecimalFilter()));

        // Stok: yalnızca tam sayı
        stockField.setTextFormatter(new TextFormatter<>(numericIntFilter()));

        // Birim: rakam yasak + uzunluk limiti
        unitField.setTextFormatter(new TextFormatter<>(unitFilter(50)));
    }

    public void setProduct(Product product) {
        this.product = product;
        if (titleLabel != null) titleLabel.setText(product == null ? "Yeni Ürün Ekle" : "Ürün Düzenle");

        if (product != null) {
            nameField.setText(product.getUrunAdi());

            BigDecimal f = Money.scale2(product.getFiyat());
            priceField.setText(Money.fmtTR(f));   // ör: 1.234,56

            stockField.setText(String.valueOf(product.getStok()));
            unitField.setText(product.getBirim());
        }
    }

    @FXML
    private void handleSave() {
        // Önce hızlı doğrulama (FX thread)
        String name = safeTrim(nameField.getText());
        String unit = safeTrim(unitField.getText()).replaceAll("\\s+", " ");

        if (name.isBlank()) { AppDialogs.warn("Ürün adı boş olamaz."); return; }
        if (unit.isBlank()) { AppDialogs.warn("Birim boş olamaz.");   return; }

        BigDecimal price = parsePriceBD(priceField.getText());
        if (price == null)        { AppDialogs.warn("Fiyat girin (örn. 12,50).");   return; }
        if (price.signum() < 0)   { AppDialogs.warn("Fiyat negatif olamaz.");       return; }

        Integer stock = parseInt(stockField.getText());
        if (stock == null)        { AppDialogs.warn("Stok sayısal bir tam sayı olmalı."); return; }
        if (stock < 0)            { AppDialogs.warn("Stok negatif olamaz.");              return; }

        // UI’yi kilitle ve asenkron kaydet
        setBusy(true);

        final boolean isCreate = (product == null);
        final String  finalName  = name;
        final BigDecimal finalPrice = price;
        final int     finalStock = stock;
        final String  finalUnit  = unit;

        Async.runVoid(
                () -> {
                    try {
                        if (isCreate) {
                            ProductDAO.addProduct(finalName, finalPrice, finalStock, finalUnit);
                        } else {
                            product.setUrunAdi(finalName);
                            product.setFiyat(finalPrice);
                            product.setStok(finalStock);
                            product.setBirim(finalUnit);
                            ProductDAO.updateProduct(product);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> {
                    AppDialogs.info(isCreate ? "Yeni ürün başarıyla eklendi."
                            : "Ürün bilgileri başarıyla güncellendi.");
                    closeWindowIfPossible();
                },
                ex -> AppDialogs.dbError("Ürün kaydetme", toSql(ex)),
                () -> setBusy(false)
        );
    }

    @FXML
    private void handleCancel() { closeWindowIfPossible(); }

    private void setBusy(boolean busy) {
        // Buton referanslarımız yok; alanları ve (varsa) pencere kökünü kilitleyelim
        if (nameField  != null) nameField.setDisable(busy);
        if (priceField != null) priceField.setDisable(busy);
        if (stockField != null) stockField.setDisable(busy);
        if (unitField  != null) unitField.setDisable(busy);

        if (dialogStage != null && dialogStage.getScene() != null) {
            var root = dialogStage.getScene().getRoot();
            if (root != null) root.setDisable(busy);
        }
    }

    private void closeWindowIfPossible() {
        if (nameField != null && nameField.getScene() != null && nameField.getScene().getWindow() != null) {
            var w = nameField.getScene().getWindow();
            if (w instanceof Stage s) s.close(); else w.hide();
        } else if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }

    /** Virgül/nokta ayracını kabul eder, 2 ondalığa yuvarlanmış BigDecimal döndürür. */
    private static BigDecimal parsePriceBD(String raw) {
        if (raw == null) return null;
        String txt = raw.trim();
        if (txt.isEmpty()) return null;
        try {
            return Money.scale2(Money.parseTR(txt));
        } catch (java.text.ParseException ex) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException ex) { return null; }
    }

    private static UnaryOperator<TextFormatter.Change> numericIntFilter() {
        return change -> change.getControlNewText().matches("\\d*") ? change : null;
    }

    /** Birim alanı filtresi: rakam yasak; opsiyonel uzunluk sınırı. */
    private static UnaryOperator<TextFormatter.Change> unitFilter(int maxLen) {
        // Rakam içermesin (tüm Unicode rakamlar için \\p{Digit})
        Pattern noDigits = Pattern.compile("^[^\\p{Digit}]*$");
        return change -> {
            String next = change.getControlNewText();
            if (next == null) return change; // güvence
            if (maxLen > 0 && next.length() > maxLen) return null;
            return noDigits.matcher(next).matches() ? change : null;
        };
    }

    /** Throwable → SQLException (zincirde varsa onu döndürür) */
    private static SQLException toSql(Throwable t) {
        if (t instanceof SQLException se) return se;
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof SQLException se) return se;
            c = c.getCause();
        }
        return new SQLException(t.getMessage(), t);
    }
}
