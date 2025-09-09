package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;

public class ViewRequestController {

    @FXML private Label requestIdLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<RequestItem> requestItemsTable;
    @FXML private TableColumn<RequestItem, String> productNameColumn;
    @FXML private TableColumn<RequestItem, Integer> quantityColumn;
    @FXML private TableColumn<RequestItem, Double> discountedPriceColumn;

    private Stage dialogStage;

    @FXML
    public void initialize() {
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setRequest(Request request) {
        requestIdLabel.setText(String.valueOf(request.getId()));
        statusLabel.setText(request.getStatus());

        try {
            Customer customer = CustomerDAO.getCustomerById(request.getCustomerId());
            customerNameLabel.setText(customer.getCompanyName());

            ObservableList<RequestItem> items = RequestDAO.getRequestItemsByRequestId(request.getId());
            requestItemsTable.setItems(items);
        } catch (SQLException e) {
            showAlert("Hata", "Talep detayları yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleClose() {
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