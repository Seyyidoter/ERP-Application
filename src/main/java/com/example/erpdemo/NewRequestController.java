package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.function.UnaryOperator;

public class NewRequestController {

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product>  productComboBox;
    @FXML private TextField quantityField;

    @FXML private TableView<RequestItem> productTable;
    @FXML private TableColumn<RequestItem, String>     productNameColumn;
    @FXML private TableColumn<RequestItem, Integer>    quantityColumn;
    @FXML private TableColumn<RequestItem, BigDecimal> priceColumn;           // ListPrice
    @FXML private TableColumn<RequestItem, BigDecimal> discountedPriceColumn; // DiscountedPrice

    @FXML private Label totalAmountLabel;

    private final ObservableList<RequestItem> requestItems = FXCollections.observableArrayList();
    private Stage dialogStage;

    @FXML
    public void initialize() {
        try {
            customerComboBox.setItems(CustomerDAO.getAllCustomers());
            customerComboBox.setConverter(new CustomerStringConverter());

            productComboBox.setItems(ProductDAO.getAllProducts());
            productComboBox.setConverter(new ProductStringConverter());
        } catch (SQLException e) {
            AppDialogs.dbError("Müşteri/ürün verileri yükleme", e);
        }

        // kolon–model bağları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("listPrice"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        productTable.setItems(requestItems);

        // fiyat kolonlarını TR formatında göster
        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null :
                        String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", v));
            }
        });
        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null :
                        String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", v));
            }
        });

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        // miktar alanına sadece tam sayı filtresi
        quantityField.setTextFormatter(new TextFormatter<>(numericIntFilter()));
    }

    @FXML
    private void handleAddProduct() {
        Customer cus = customerComboBox.getSelectionModel().getSelectedItem();
        Product  prd = productComboBox.getSelectionModel().getSelectedItem();

        if (cus == null || prd == null || quantityField.getText().isBlank()) {
            AppDialogs.warn("Lütfen müşteri, ürün ve miktar girin.");
            return;
        }

        int qty;
        try { qty = Integer.parseInt(quantityField.getText().trim()); }
        catch (NumberFormatException e) { AppDialogs.warn("Miktar sayısal olmalı."); return; }
        if (qty <= 0) { AppDialogs.warn("Miktar 0'dan büyük olmalı."); return; }

        // Stoksuz sürüm: stok yeterliliği kontrolü YOK

        BigDecimal listPrice   = prd.getFiyat() == null ? BigDecimal.ZERO
                : prd.getFiyat().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountPct = BigDecimal.valueOf(cus.getIskonto()); // yüzde (int)
        BigDecimal discounted  = listPrice
                .multiply(BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        requestItems.add(new RequestItem(
                0,                // itemId (DB henüz yok)
                0,                // requestId (DB henüz yok)
                prd.getId(),
                prd.getUrunAdi(),
                qty,
                listPrice,
                discounted
        ));

        quantityField.clear();
        updateTotalAmount();
    }

    @FXML
    private void handleSaveRequest() {
        Customer cus = customerComboBox.getSelectionModel().getSelectedItem();
        if (cus == null || requestItems.isEmpty()) {
            AppDialogs.warn("Müşteri seçin ve en az bir ürün ekleyin.");
            return;
        }

        try {
            // Stoksuz sürüm: güncel stok/doğrulama YOK, doğrudan kaydet
            int requestId = RequestDAO.addRequest(cus.getId());
            if (requestId != -1) {
                for (RequestItem it : requestItems) {
                    RequestDAO.addRequestItem(
                            requestId,
                            it.getProductId(),
                            it.getQuantity(),
                            it.getDiscountedPrice() // BigDecimal
                    );
                }
                AppDialogs.info("Talep kaydedildi.");
                if (dialogStage != null) dialogStage.close();
                else closeWindowIfPossible();
            } else {
                AppDialogs.error("Talep kaydedilemedi.");
            }
        } catch (SQLException e) {
            AppDialogs.dbError("Talep kaydı", e);
        }
    }

    @FXML
    private void handleCancel() {
        if (dialogStage != null) dialogStage.close();
        else closeWindowIfPossible();
    }

    private void closeWindowIfPossible() {
        if (productTable != null && productTable.getScene() != null) {
            productTable.getScene().getWindow().hide();
        }
    }

    public void setDialogStage(Stage s) { this.dialogStage = s; }

    private void updateTotalAmount() {
        BigDecimal total = requestItems.stream()
                .map(RequestItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmountLabel.setText(String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f TL", total));
    }

    private static UnaryOperator<TextFormatter.Change> numericIntFilter() {
        return change -> change.getControlNewText().matches("\\d*") ? change : null;
    }
}
