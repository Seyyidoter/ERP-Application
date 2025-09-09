package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
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
        int discount = Integer.parseInt(discountField.getText());

        try {
            CustomerDAO.addCustomer(companyName, contactPerson, phone, email, discount);
            showAlert("Başarılı", "Yeni müşteri başarıyla eklendi.");
            // Pencereyi kapat
            Stage stage = (Stage) companyNameField.getScene().getWindow();
            stage.close();
        } catch (SQLException | NumberFormatException e) {
            showAlert("Hata", "Müşteri eklenirken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        // Pencereyi kapatır
        Stage stage = (Stage) companyNameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}