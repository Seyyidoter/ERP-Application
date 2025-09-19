package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class CustomerController {

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String>  nameColumn;
    @FXML private TableColumn<Customer, String>  contactColumn;
    @FXML private TableColumn<Customer, String>  phoneColumn;
    @FXML private TableColumn<Customer, String>  emailColumn;
    @FXML private TableColumn<Customer, Integer> iskontoColumn;
    @FXML private TableColumn<Customer, Double>  balanceColumn; // Bakiye

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));

        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("bakiye"));
        balanceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(String.format("%.2f", v));
                setStyle("-fx-alignment: CENTER-RIGHT;" + (v < 0 ? " -fx-text-fill: #c62828;" : ""));
            }
        });

        loadCustomers();
    }

    private void loadCustomers() {
        try {
            ObservableList<Customer> customerList = CustomerDAO.getAllCustomers();
            customerTable.setItems(customerList);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Hata", "Müşteri verileri yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-customer.fxml"));
            Parent parent = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Müşteri Ekle");
            stage.setScene(new Scene(parent));
            IconUtil.setAppIcon(stage);
            stage.showAndWait();
            loadCustomers();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Hata", "Yeni müşteri penceresi açılamıyor.");
        }
    }

    @FXML
    private void handleEditButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("edit-customer.fxml"));
                Parent parent = loader.load();

                EditCustomerController controller = loader.getController();

                Stage dialogStage = new Stage();
                dialogStage.setTitle("Müşteri Düzenle");
                dialogStage.initModality(Modality.APPLICATION_MODAL);
                dialogStage.setScene(new Scene(parent));
                IconUtil.setAppIcon(dialogStage);

                controller.setDialogStage(dialogStage);
                controller.setCustomer(selectedCustomer);

                dialogStage.showAndWait();
                loadCustomers();

            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Hata", "Müşteri düzenleme penceresi açılamadı.");
            }
        } else {
            showAlert("Uyarı", "Lütfen düzenlemek için bir müşteri seçin.");
        }
    }

    @FXML
    private void handleDeleteButton() {
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if (selectedCustomer != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Müşteriyi silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            IconUtil.decorateAlert(confirm);
            confirm.showAndWait();

            if (confirm.getResult() == ButtonType.YES) {
                try {
                    CustomerDAO.deleteCustomer(selectedCustomer.getId());
                    showAlert("Başarılı", "Müşteri başarıyla silindi.");
                    loadCustomers();
                } catch (SQLException e) {
                    e.printStackTrace();
                    String msg = e.getMessage();
                    if (msg != null && msg.toLowerCase().contains("foreign key")) {
                        showAlert("Hata", "Kayıt başka veriler tarafından kullanılıyor. Önce ilişkili kayıtları silin.");
                    } else {
                        showAlert("Hata", "Müşteri silinirken bir hata oluştu: " + e.getMessage());
                    }
                }
            }
        } else {
            showAlert("Uyarı", "Lütfen silmek için bir müşteri seçin.");
        }
    }

    // ----- Ödeme Al -----
    @FXML
    private void handleTakePayment() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("Uyarı", "Lütfen ödeme alınacak müşteriyi seçin."); return; }

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Ödeme Al – " + sel.getCompanyName());
        dlg.setHeaderText(null);
        dlg.setContentText("Tutar (TL):");
        IconUtil.decorateDialog(dlg);
        var res = dlg.showAndWait();
        if (res.isEmpty()) return;

        double amount;
        try { amount = Double.parseDouble(res.get().replace(",", ".")); }
        catch (NumberFormatException e) { showAlert("Hata","Geçerli bir sayı girin."); return; }
        if (amount <= 0) { showAlert("Uyarı","Tutar sıfırdan büyük olmalı."); return; }

        TextInputDialog noteDlg = new TextInputDialog();
        noteDlg.setTitle("Açıklama");
        noteDlg.setHeaderText(null);
        noteDlg.setContentText("Açıklama (opsiyonel):");
        IconUtil.decorateDialog(noteDlg);
        String note = noteDlg.showAndWait().orElse("");

        try {
            PaymentDAO.addPayment(sel.getId(), amount, note);
            showAlert("Başarılı", String.format("Ödeme kaydedildi (%.2f TL).", amount));
            loadCustomers();
        } catch (SQLException ex) {
            showAlert("Hata", "Ödeme kaydedilemedi: " + ex.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);
        alert.showAndWait();
    }
}
