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
        unitField.setText(product.getBirim());
    }

    @FXML
    private void handleSave() {
        try {
            String name = nameField.getText();
            String unit = unitField.getText();

            if (name == null || name.isBlank()) {
                showAlert("Uyarı", "Ürün adı boş olamaz."); return;
            }
            if (unit == null || unit.isBlank()) {
                showAlert("Uyarı", "Birim boş olamaz."); return;
            }

            // Virgül/nokta toleransı
            String priceText = priceField.getText().replace(",", ".");
            double price = Double.parseDouble(priceText);
            if (price < 0) { showAlert("Uyarı", "Fiyat negatif olamaz."); return; }

            if (product == null) {
                // Yeni ürün: stok önemsenmiyor → 0 yazıyoruz
                ProductDAO.addProduct(name, price, 0, unit);
                showAlert("Başarılı", "Yeni ürün başarıyla eklendi.");
            } else {
                // Mevcut stok değerine dokunmuyoruz (Product içinde neyse o kalır)
                product.setUrunAdi(name);
                product.setFiyat(price);
                product.setBirim(unit);
                ProductDAO.updateProduct(product);
                showAlert("Başarılı", "Ürün bilgileri başarıyla güncellendi.");
            }
            dialogStage.close();
        } catch (NumberFormatException e) {
            showAlert("Hata", "Fiyat sayısal olmalı.");
        } catch (SQLException e) {
            showAlert("Hata", "İşlem sırasında bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
