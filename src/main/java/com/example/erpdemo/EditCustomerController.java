package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;

public class EditCustomerController {

    @FXML private TextField idField;
    @FXML private TextField companyNameField;
    @FXML private TextField contactPersonField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField discountField;

    private Customer customer;
    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        idField.setText(String.valueOf(customer.getId()));
        companyNameField.setText(customer.getCompanyName());
        contactPersonField.setText(customer.getContactPerson());
        phoneField.setText(customer.getPhone());
        emailField.setText(customer.getEmail());
        discountField.setText(String.valueOf(customer.getIskonto()));
    }

    @FXML
    private void handleSave() {
        customer.setCompanyName(companyNameField.getText());
        customer.setContactPerson(contactPersonField.getText());
        customer.setPhone(phoneField.getText());
        customer.setEmail(emailField.getText());
        customer.setIskonto(Integer.parseInt(discountField.getText()));

        try {
            CustomerDAO.updateCustomer(customer);
            showAlert("Başarılı", "Müşteri bilgileri başarıyla güncellendi.");
            dialogStage.close();
        } catch (SQLException | NumberFormatException e) {
            showAlert("Hata", "Müşteri güncellenirken bir hata oluştu: " + e.getMessage());
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