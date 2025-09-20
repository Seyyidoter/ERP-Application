package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class NewRequestController {

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product>  productComboBox;
    @FXML private TextField quantityField;

    @FXML private TableView<RequestItem> productTable;
    @FXML private TableColumn<RequestItem, String>  productNameColumn;
    @FXML private TableColumn<RequestItem, Integer> quantityColumn;
    @FXML private TableColumn<RequestItem, Double>  priceColumn;
    @FXML private TableColumn<RequestItem, Double>  discountedPriceColumn;

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
            showAlert("Hata", "Müşteri/ürün verileri yüklenemedi.");
        }

        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        productTable.setItems(requestItems);

        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.2f", value));
            }
        });

        // --- UI dokunuşu: miktar sütunu sağa hizalı ---
        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /** Aynı üründen tabloda zaten ekli miktarları toplar. */
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
            showAlert("Uyarı", "Lütfen müşteri, ürün ve miktar girin.");
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(quantityField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Hata", "Miktar sayısal olmalı.");
            return;
        }
        if (qty <= 0) {
            showAlert("Uyarı", "Miktar 0'dan büyük olmalı.");
            return;
        }

        // ---- STOK KONTROLÜ (listede aynı üründen mevcut miktarı da dahil ederek) ----
        int alreadyAdded = collectQuantitiesByProduct().getOrDefault(prd.getId(), 0);
        if (qty + alreadyAdded > prd.getStok()) {
            showAlert("Uyarı", "Stok yetersiz! (Stok: " + prd.getStok() +
                    ", Listede mevcut: " + alreadyAdded + ", Eklemek istediğiniz: " + qty + ")");
            return;
        }

        double price = prd.getFiyat();
        double discounted = price;
        if (cus != null) {
            discounted = price - (price * cus.getIskonto() / 100.0);
        }

        requestItems.add(new RequestItem(
                0, // id
                0, // requestId (kaydedilince verilecek)
                prd.getId(),
                prd.getUrunAdi(),
                qty,
                price,
                discounted
        ));

        quantityField.clear();
        updateTotalAmount();
    }

    @FXML
    private void handleSaveRequest() {
        Customer cus = customerComboBox.getSelectionModel().getSelectedItem();
        if (cus == null || requestItems.isEmpty()) {
            showAlert("Uyarı", "Müşteri seçin ve en az bir ürün ekleyin.");
            return;
        }

        try {
            // ======== SON STOK KONTROLÜ (KAYIT ANINDA, GÜNCEL DB DEĞERİYLE) ========
            // Aynı ürünlerden gelen miktarları topla
            Map<Integer, Integer> totals = collectQuantitiesByProduct();

            // Her ürün için veritabanından güncel stok çek, karşılaştır
            List<String> insuff = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : totals.entrySet()) {
                int productId = e.getKey();
                int requested = e.getValue();

                Product latest = ProductDAO.getProductById(productId);
                if (latest == null) {
                    insuff.add("Ürün bulunamadı (ID: " + productId + ")");
                    continue;
                }
                if (latest.getStok() < requested) {
                    insuff.add(latest.getUrunAdi() + " — İstenen: " + requested +
                            ", Güncel Stok: " + latest.getStok());
                }
            }
            if (!insuff.isEmpty()) {
                String msg = "Aşağıdaki kalemlerde stok yetersiz olduğu için talep kaydedilmedi:\n\n" +
                        insuff.stream().collect(Collectors.joining("\n"));
                showAlert("Uyarı", msg);
                return;
            }
            // =====================================================================

            // KAYDET: başlık + kalemler (kalem fiyatları İSKONTOLU fiyattır)
            int requestId = RequestDAO.addRequest(cus.getId());
            if (requestId != -1) {
                for (RequestItem it : requestItems) {
                    RequestDAO.addRequestItem(requestId, it.getProductId(), it.getQuantity(), it.getDiscountedPrice());
                }
                showAlert("Başarılı", "Talep kaydedildi.");
                if (dialogStage != null) dialogStage.close();
                else closeWindowIfPossible();
            } else {
                showAlert("Hata", "Talep kaydedilemedi.");
            }
        } catch (SQLException e) {
            showAlert("Hata", "Veritabanı hatası: " + e.getMessage());
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
        double total = requestItems.stream()
                .mapToDouble(i -> i.getDiscountedPrice() * i.getQuantity())
                .sum();
        totalAmountLabel.setText(String.format("%.2f TL", total));
    }

    private void showAlert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle(title);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
