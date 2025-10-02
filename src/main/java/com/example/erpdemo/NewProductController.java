package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.UnaryOperator;

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
        // Sayısal alanlar için sınırlandırma
        priceField.setTextFormatter(new TextFormatter<>(numericDecimalFilter()));
        stockField.setTextFormatter(new TextFormatter<>(numericIntFilter()));

        // BİRİM alanında SAYI YAZMAYI ENGELLE
        unitField.setTextFormatter(new TextFormatter<>(change -> {
            String t = change.getText();
            // silme/taşıma işlemlerinde t genelde "" olur, engelleme
            if (t == null || t.isEmpty()) return change;
            // eklenen/paste edilen metinde herhangi bir rakam varsa engelle
            return t.matches(".*\\d.*") ? null : change;
        }));
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
        try {
            String name = safeTrim(nameField.getText());
            String unit = safeTrim(unitField.getText());
            if (name.isBlank()) { AppDialogs.warn("Ürün adı boş olamaz."); return; }
            if (unit.isBlank()) { AppDialogs.warn("Birim boş olamaz.");   return; }

            BigDecimal price = parsePriceBD(priceField.getText());
            if (price == null) { AppDialogs.warn("Fiyat girin (örn. 12,50)."); return; }
            if (price.signum() < 0) { AppDialogs.warn("Fiyat negatif olamaz."); return; }

            Integer stock = parseInt(stockField.getText());
            if (stock == null) { AppDialogs.warn("Stok sayısal bir tam sayı olmalı."); return; }
            if (stock < 0)     { AppDialogs.warn("Stok negatif olamaz.");              return; }

            if (product == null) {
                ProductDAO.addProduct(name, price, stock, unit);
                AppDialogs.info("Yeni ürün başarıyla eklendi.");
            } else {
                product.setUrunAdi(name);
                product.setFiyat(price);
                product.setStok(stock);
                product.setBirim(unit);
                ProductDAO.updateProduct(product);
                AppDialogs.info("Ürün bilgileri başarıyla güncellendi.");
            }

            closeWindowIfPossible();

        } catch (Exception e) {
            if (e instanceof java.sql.SQLException se) {
                AppDialogs.dbError("Ürün kaydetme", se);
            } else {
                AppDialogs.unexpectedError("Ürün kaydetme", e);
            }
        }
    }

    @FXML
    private void handleCancel() { closeWindowIfPossible(); }

    private void closeWindowIfPossible() {
        if (nameField != null && nameField.getScene() != null) {
            var w = nameField.getScene().getWindow();
            if (w instanceof Stage s) s.close(); else w.hide();
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

    private static UnaryOperator<TextFormatter.Change> numericDecimalFilter() {
        return change -> {
            String s = change.getControlNewText();
            if (s.isEmpty()) return change;
            if (!s.matches("[0-9.,]*")) return null;
            long sep = s.chars().filter(ch -> ch == '.' || ch == ',').count();
            return sep <= 1 ? change : null;
        };
    }
}
