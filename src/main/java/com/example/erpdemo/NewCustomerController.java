package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;

public class NewCustomerController {

    @FXML private TextField companyNameField;
    @FXML private TextField contactPersonField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField discountField;

    @FXML
    private void handleSave() {
        String companyName = companyNameField.getText();
        String contactPerson = contactPersonField.getText();
        String phone = phoneField.getText();
        String email = emailField.getText();

        Integer discount = parseIntOrNull(discountField.getText());
        if (discount == null || discount < 0) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "İskonto geçerli bir sayı olmalı (0 veya üzeri).");
            return;
        }

        try {
            CustomerDAO.addCustomer(companyName, contactPerson, phone, email, discount);
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Yeni müşteri başarıyla eklendi.");
            Stage stage = (Stage) companyNameField.getScene().getWindow();
            stage.close();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Müşteri eklenirken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        Stage stage = (Stage) companyNameField.getScene().getWindow();
        stage.close();
    }

    private Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);

        alert.showAndWait();
    }
}
