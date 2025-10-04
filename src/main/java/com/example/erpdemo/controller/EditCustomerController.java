package com.example.erpdemo.controller;

import com.example.erpdemo.util.AppDialogs;
import com.example.erpdemo.model.Customer;
import com.example.erpdemo.dao.CustomerDAO;
import javafx.fxml.FXML;
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

    private Stage dialogStage;
    private Customer customer; // düzenlenecek mevcut müşteri

    public void setDialogStage(Stage stage) { this.dialogStage = stage; }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer == null) return;

        idField.setText(String.valueOf(customer.getId()));
        companyNameField.setText(customer.getCompanyName());
        contactPersonField.setText(customer.getContactPerson());
        phoneField.setText(customer.getPhone());
        emailField.setText(customer.getEmail());
        discountField.setText(String.valueOf(customer.getIskonto())); // int %
    }

    @FXML
    private void handleSave() {
        if (customer == null) { AppDialogs.error("Düzenlenecek müşteri bulunamadı."); return; }

        String company = trim(companyNameField.getText());
        String contact = trim(contactPersonField.getText());
        String phone   = trim(phoneField.getText());
        String email   = trim(emailField.getText());
        String discTxt = discountField.getText() == null ? "" : discountField.getText().trim();

        if (company.isEmpty()) { AppDialogs.warn("Firma adı boş olamaz."); return; }

        int discount;
        if (discTxt.isEmpty()) {
            discount = 0;
        } else {
            try {
                // iskonto % tam sayı olarak tutuluyor
                if (discTxt.contains(".") || discTxt.contains(",")) {
                    AppDialogs.warn("İskonto yüzdesi tam sayı olmalıdır (örn. 0, 5, 10…).");
                    return;
                }
                discount = Integer.parseInt(discTxt);
            } catch (NumberFormatException nfe) {
                AppDialogs.warn("İskonto değeri sayısal olmalı (örn. 0, 5, 10).");
                return;
            }
        }
        if (discount < 0 || discount > 100) {
            AppDialogs.warn("İskonto yüzdesi 0 ile 100 arasında olmalıdır.");
            return;
        }

        try {
            customer.setCompanyName(company);
            customer.setContactPerson(contact);
            customer.setPhone(phone);
            customer.setEmail(email);
            customer.setIskonto(discount);

            CustomerDAO.updateCustomer(customer);
            AppDialogs.info("Müşteri bilgileri güncellendi.");
            closeWindow();
        } catch (SQLException e) {
            AppDialogs.dbError("Müşteri güncelleme", e);
        }
    }

    @FXML
    private void handleCancel() { closeWindow(); }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private void closeWindow() {
        if (dialogStage != null) dialogStage.close();
        else if (companyNameField != null && companyNameField.getScene() != null) {
            companyNameField.getScene().getWindow().hide();
        }
    }
}
