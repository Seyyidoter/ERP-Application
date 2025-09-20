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
        // --- Temel alan kontrolleri ---
        String companyName = safeTrim(companyNameField.getText());
        if (companyName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "Firma adı boş olamaz.");
            return;
        }

        String contact = safeTrim(contactPersonField.getText());
        String phone    = safeTrim(phoneField.getText());
        String email    = safeTrim(emailField.getText());

        // --- İskonto: 0..100 arası TAM SAYI olmalı ---
        String discountRaw = safeTrim(discountField.getText());
        if (discountRaw.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "İskonto alanı boş olamaz.");
            return;
        }

        // Yalnızca rakam kontrolü
        if (!discountRaw.matches("^\\d{1,3}$")) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "İskonto yalnızca rakamlardan oluşan bir tam sayı olmalıdır.");
            return;
        }

        int discount;
        try {
            discount = Integer.parseInt(discountRaw);
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "İskonto geçerli bir tam sayı olmalıdır.");
            return;
        }

        if (discount < 0 || discount > 100) {
            showAlert(Alert.AlertType.WARNING, "Uyarı", "İskonto 0 ile 100 arasında bir tam sayı olmalıdır.");
            return;
        }

        // --- Modeli güncelle ---
        customer.setCompanyName(companyName);
        customer.setContactPerson(contact);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setIskonto(discount);

        try {
            CustomerDAO.updateCustomer(customer);
            showAlert(Alert.AlertType.INFORMATION, "Başarılı", "Müşteri bilgileri başarıyla güncellendi.");
            if (dialogStage != null) dialogStage.close();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hata", "Müşteri güncellenirken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        if (dialogStage != null) dialogStage.close();
    }

    // ---------------- yardımcılar ----------------
    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        IconUtil.decorateAlert(alert);
        alert.showAndWait();
    }
}
