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
        try {
            if (product == null) {
                ProductDAO.addProduct(
                        nameField.getText(),
                        Double.parseDouble(priceField.getText()),
                        Integer.parseInt(stockField.getText()),
                        unitField.getText()
                );
                showAlert("Başarılı", "Yeni ürün başarıyla eklendi.");
            } else {
                product.setUrunAdi(nameField.getText());
                product.setFiyat(Double.parseDouble(priceField.getText()));
                product.setStok(Integer.parseInt(stockField.getText()));
                product.setBirim(unitField.getText());
                ProductDAO.updateProduct(product);
                showAlert("Başarılı", "Ürün bilgileri başarıyla güncellendi.");
            }
            dialogStage.close();
        } catch (SQLException | NumberFormatException e) {
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