package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class CustomerController {

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> contactColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, String> emailColumn;
    @FXML private TableColumn<Customer, Integer> iskontoColumn;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        contactColumn.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        iskontoColumn.setCellValueFactory(new PropertyValueFactory<>("iskonto"));

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

            // >>> İKON
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

                // >>> İKON
                IconUtil.setAppIcon(dialogStage);

                controller.setDialogStage(dialogStage);
                controller.setCustomer(selectedCustomer);

                dialogStage.showAndWait();
                loadCustomers();

            } catch (IOException e) {
                e.printStackTrace();
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

            // >>> ALERT İKON
            IconUtil.decorateAlert(confirm);

            confirm.showAndWait();

            if (confirm.getResult() == ButtonType.YES) {
                try {
                    CustomerDAO.deleteCustomer(selectedCustomer.getId());
                    showAlert("Başarılı", "Müşteri başarıyla silindi.");
                    loadCustomers();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert("Hata", "Müşteri silinirken bir hata oluştu: " + e.getMessage());
                }
            }
        } else {
            showAlert("Uyarı", "Lütfen silmek için bir müşteri seçin.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);

        // >>> ALERT İKON
        IconUtil.decorateAlert(alert);

        alert.showAndWait();
    }
}
