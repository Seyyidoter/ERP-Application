package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;

public class NewProductController {

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private TextField unitField;

    private Stage dialogStage;
    private Product product;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

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
        String name = nameField.getText();
        Double price = parseDoubleOrNull(priceField.getText());
        Integer stock = parseIntOrNull(stockField.getText());
        String unit = unitField.getText();

        if (name == null || name.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Ürün adı boş olamaz."); return;
        }
        if (price == null || price < 0) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Fiyat geçerli bir sayı olmalı (0 veya üzeri)."); return;
        }
        if (stock == null || stock < 0) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Stok geçerli bir tam sayı olmalı (0 veya üzeri)."); return;
        }
        if (unit == null || unit.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Birim boş olamaz."); return;
        }

        try {
            if (product == null) {
                // Yeni ürün ekleme
                ProductDAO.addProduct(name, price, stock, unit);
                showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Yeni ürün başarıyla eklendi.");
            } else {
                // Ürün bilgilerini güncelleme
                product.setUrunAdi(name);
                product.setFiyat(price);
                product.setStok(stock);
                product.setBirim(unit);
                ProductDAO.updateProduct(product);
                showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Ürün bilgileri başarıyla güncellendi.");
            }
            dialogStage.close();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "İşlem sırasında bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }
    private Double parseDoubleOrNull(String s) {
        try { return Double.valueOf(s.trim().replace(",", ".")); } catch (Exception e) { return null; }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
