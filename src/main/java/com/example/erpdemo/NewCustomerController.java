package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.sql.SQLException;

public class NewCustomerController {

    @FXML private TextField companyNameField;
    @FXML private TextField contactPersonField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField discountField;

    @FXML
    private void handleSave() {
        String company = trim(companyNameField.getText());
        String contact = trim(contactPersonField.getText());
        String phone   = trim(phoneField.getText());
        String email   = trim(emailField.getText());
        String discTxt = discountField.getText() == null ? "" : discountField.getText().trim().replace(",", ".");

        if (company.isEmpty()) { AppDialogs.warn("Firma adı boş olamaz."); return; }

        int discount;
        if (discTxt.isEmpty()) {
            discount = 0;
        } else {
            try {
                // “12.5” gibi değer girilmişse yuvarlama yapmadan int gerekir -> sadece tam sayı kabul edelim:
                if (discTxt.contains(".")) {
                    AppDialogs.warn("İskonto yüzdesi tam sayı olmalıdır (örn. 0, 5, 10…).");
                    return;
                }
                discount = Integer.parseInt(discTxt);
            } catch (NumberFormatException nfe) {
                AppDialogs.error("İskonto değeri sayısal olmalı (örn. 0, 5, 10).");
                return;
            }
        }
        if (discount < 0 || discount > 100) {
            AppDialogs.warn("İskonto yüzdesi 0 ile 100 arasında olmalıdır.");
            return;
        }

        try {
            // DAO imzana göre uyarlayın; tipler: (String, String, String, String, int)
            CustomerDAO.addCustomer(company, contact, phone, email, discount);
            AppDialogs.info("Müşteri başarıyla eklendi.");
            closeWindow();
        } catch (SQLException e) {
            AppDialogs.error("Müşteri eklenemedi: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() { closeWindow(); }

    /* ------------ küçük yardımcılar ------------ */
    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private void closeWindow() {
        if (companyNameField != null && companyNameField.getScene() != null) {
            companyNameField.getScene().getWindow().hide();
        }
    }
}
