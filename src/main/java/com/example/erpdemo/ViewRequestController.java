package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;

public class ViewRequestController {

    @FXML private Label requestIdLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<RequestItem> requestItemsTable;
    @FXML private TableColumn<RequestItem, String>  productNameColumn;
    @FXML private TableColumn<RequestItem, Integer> quantityColumn;
    @FXML private TableColumn<RequestItem, Double>  discountedPriceColumn;

    private Stage dialogStage;

    @FXML
    public void initialize() {
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.2f", value));
            }
        });

        requestItemsTable.setPlaceholder(new Label("Kalem bulunmuyor"));
    }

    public void setDialogStage(Stage dialogStage) { this.dialogStage = dialogStage; }

    public void setRequest(Request request) {
        if (request == null) return;

        requestIdLabel.setText(String.valueOf(request.getId()));
        statusLabel.setText(request.getStatus() != null ? request.getStatus() : "—");

        try {
            Customer customer = CustomerDAO.getCustomerById(request.getCustomerId());
            customerNameLabel.setText(customer != null ? customer.getCompanyName() : "—");

            ObservableList<RequestItem> items = RequestDAO.getRequestItemsByRequestId(request.getId());
            requestItemsTable.setItems(items);
        } catch (SQLException e) {
            showAlert("Hata", "Talep detayları yüklenirken bir hata oluştu:\n" + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) dialogStage.close();
        else requestItemsTable.getScene().getWindow().hide();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);

        alert.showAndWait();
    }
}
