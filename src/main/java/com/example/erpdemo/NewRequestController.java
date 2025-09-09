package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;

public class NewRequestController {
    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product> productComboBox;
    @FXML private TextField quantityField;
    @FXML private TableView<RequestItem> productTable;
    @FXML private TableColumn<RequestItem, String> productNameColumn;
    @FXML private TableColumn<RequestItem, Integer> quantityColumn;
    @FXML private TableColumn<RequestItem, Double> priceColumn;
    @FXML private TableColumn<RequestItem, Double> discountedPriceColumn;
    @FXML private Label totalAmountLabel;

    private ObservableList<RequestItem> requestItems = FXCollections.observableArrayList();
    private Stage dialogStage;

    @FXML
    public void initialize() {
        try {
            customerComboBox.setItems(CustomerDAO.getAllCustomers());
            customerComboBox.setConverter(new CustomerStringConverter());
            productComboBox.setItems(ProductDAO.getAllProducts());
            productComboBox.setConverter(new ProductStringConverter());
        } catch (SQLException e) {
            showAlert("Hata", "Müşteri ve ürün verileri yüklenemedi.");
        }

        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));
        productTable.setItems(requestItems);
    }

    @FXML
    private void handleAddProduct() {
        Customer selectedCustomer = customerComboBox.getSelectionModel().getSelectedItem();
        Product selectedProduct = productComboBox.getSelectionModel().getSelectedItem();

        if (selectedCustomer == null || selectedProduct == null || quantityField.getText().isEmpty()) {
            showAlert("Uyarı", "Lütfen bir müşteri, bir ürün ve miktar seçin.");
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityField.getText());
            double originalPrice = selectedProduct.getFiyat();
            double iskontoOrani = selectedCustomer.getIskonto();
            double discountedPrice = originalPrice - (originalPrice * iskontoOrani / 100);

            RequestItem newItem = new RequestItem(0, 0, selectedProduct.getId(), selectedProduct.getUrunAdi(), quantity, originalPrice, discountedPrice);
            requestItems.add(newItem);

            updateTotalAmount();
        } catch (NumberFormatException e) {
            showAlert("Hata", "Miktar ve fiyat alanlarına geçerli sayılar girin.");
        }
    }

    @FXML
    private void handleSaveRequest() {
        Customer selectedCustomer = customerComboBox.getSelectionModel().getSelectedItem();
        if (selectedCustomer == null || requestItems.isEmpty()) {
            showAlert("Uyarı", "Lütfen bir müşteri seçin ve en az bir ürün ekleyin.");
            return;
        }

        try {
            int requestId = RequestDAO.addRequest(selectedCustomer.getId());
            if (requestId != -1) {
                for (RequestItem item : requestItems) {
                    RequestDAO.addRequestItem(requestId, item.getProductId(), item.getQuantity(), item.getDiscountedPrice());
                }
                showAlert("Başarılı", "Talep başarıyla kaydedildi.");
                dialogStage.close();
            } else {
                showAlert("Hata", "Talep kaydedilemedi.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Hata", "Talep kaydedilirken bir veritabanı hatası oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateTotalAmount() {
        double total = requestItems.stream().mapToDouble(item -> item.getDiscountedPrice() * item.getQuantity()).sum();
        totalAmountLabel.setText(String.format("%.2f TL", total));
    }
}