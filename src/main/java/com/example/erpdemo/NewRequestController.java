package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class NewRequestController {

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product>  productComboBox;
    @FXML private TextField quantityField;

    @FXML private TableView<RequestItem> productTable;
    @FXML private TableColumn<RequestItem, String>     productNameColumn;
    @FXML private TableColumn<RequestItem, Integer>    quantityColumn;
    @FXML private TableColumn<RequestItem, BigDecimal> priceColumn;           // <-- BigDecimal
    @FXML private TableColumn<RequestItem, BigDecimal> discountedPriceColumn;  // <-- BigDecimal

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

        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("listPrice"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        productTable.setItems(requestItems);

        // BigDecimal’ı 2 ondalık basamakla yazdır
        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v.doubleValue()));
            }
        });
        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v.doubleValue()));
            }
        });

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    private Map<Integer, Integer> collectQuantitiesByProduct() {
        Map<Integer, Integer> map = new HashMap<>();
        for (RequestItem it : requestItems) {
            map.merge(it.getProductId(), it.getQuantity(), Integer::sum);
        }
        return map;
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

        int alreadyAdded = collectQuantitiesByProduct().getOrDefault(prd.getId(), 0);
        if (qty + alreadyAdded > prd.getStok()) {
            AppDialogs.warn("Stok yetersiz! (Stok: " + prd.getStok() +
                    ", Listede mevcut: " + alreadyAdded + ", Eklemek istediğiniz: " + qty + ")");
            return;
        }

        double price = prd.getFiyat();
        double discounted = price - (price * cus.getIskonto() / 100.0);

        requestItems.add(new RequestItem(0, 0, prd.getId(), prd.getUrunAdi(), qty, price, discounted));

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
            Map<Integer, Integer> totals = collectQuantitiesByProduct();

            List<String> insuff = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : totals.entrySet()) {
                int productId = e.getKey();
                int requested = e.getValue();

                Product latest = ProductDAO.getProductById(productId);
                if (latest == null) { insuff.add("Ürün bulunamadı (ID: " + productId + ")"); continue; }
                if (latest.getStok() < requested) {
                    insuff.add(latest.getUrunAdi() + " — İstenen: " + requested +
                            ", Güncel Stok: " + latest.getStok());
                }
            }
            if (!insuff.isEmpty()) {
                String msg = "Aşağıdaki kalemlerde stok yetersiz olduğu için talep kaydedilmedi:\n\n" +
                        insuff.stream().collect(Collectors.joining("\n"));
                AppDialogs.warn(msg);
                return;
            }

            int requestId = RequestDAO.addRequest(cus.getId());
            if (requestId != -1) {
                for (RequestItem it : requestItems) {
                    RequestDAO.addRequestItem(requestId,
                            it.getProductId(),
                            it.getQuantity(),
                            it.getDiscountedPrice().doubleValue());
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
        totalAmountLabel.setText(String.format("%.2f TL", total.doubleValue()));
    }
}
