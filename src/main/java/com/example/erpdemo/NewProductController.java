package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;

public class NewProductController {

    @FXML private javafx.scene.control.Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private TextField unitField;

    private Stage dialogStage;
    private Product product;

    public void setDialogStage(Stage dialogStage) { this.dialogStage = dialogStage; }

    public void setProduct(Product product) {
        this.product = product;
        titleLabel.setText("Ürün Düzenle");
        nameField.setText(product.getUrunAdi());
        priceField.setText(String.valueOf(product.getFiyat()));
        stockField.setText(String.valueOf(product.getStok()));
        unitField.setText(product.getBirim());
    }

    @FXML
    private void handleSave() {
        try {
            String name = nameField.getText();
            String unit = unitField.getText();

            if (name == null || name.isBlank()) { AppDialogs.warn("Ürün adı boş olamaz."); return; }
            if (unit == null || unit.isBlank()) { AppDialogs.warn("Birim boş olamaz."); return; }

            String priceText = priceField.getText().replace(",", ".");
            double price = Double.parseDouble(priceText);
            if (price < 0) { AppDialogs.warn("Fiyat negatif olamaz."); return; }

            int stock;
            try { stock = Integer.parseInt(stockField.getText().trim()); }
            catch (NumberFormatException ex) { AppDialogs.warn("Stok sayısal bir tam sayı olmalı."); return; }
            if (stock < 0) { AppDialogs.warn("Stok negatif olamaz."); return; }

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
            if (dialogStage != null) dialogStage.close();
        } catch (NumberFormatException e) {
            AppDialogs.warn("Fiyat sayısal olmalı.");
        } catch (SQLException e) {
            AppDialogs.dbError("Ürün kaydetme", e);
        }
    }

    @FXML
    private void handleCancel() { if (dialogStage != null) dialogStage.close(); }
}
