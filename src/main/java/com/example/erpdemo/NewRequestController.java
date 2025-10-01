package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class NewRequestController {

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product>  productComboBox;
    @FXML private TextField quantityField;

    @FXML private TableView<RequestItem> productTable;
    @FXML private TableColumn<RequestItem, String>     productNameColumn;
    @FXML private TableColumn<RequestItem, Integer>    quantityColumn;
    @FXML private TableColumn<RequestItem, BigDecimal> priceColumn;           // BigDecimal
    @FXML private TableColumn<RequestItem, BigDecimal> discountedPriceColumn; // BigDecimal

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

        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", v));
            }
        });
        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", v));
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

        BigDecimal price = prd.getFiyat(); // BigDecimal (liste fiyat)
        BigDecimal discountPct = BigDecimal.valueOf(cus.getIskonto()); // % int
        BigDecimal discounted = price
                .multiply(BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

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
            // 1) Güncel stoklara göre toplamlara bak (yarışları azaltmak için son kontrol)
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

            // 2) ATOMİK KAYIT: Başlık + Kalemler aynı transaction’da
            final String insertHeaderSql = """
                INSERT INTO dbo.Talepler (MusteriId, TalepTarihi, Durum)
                VALUES (?, GETDATE(), N'Onay Bekliyor')
                """;
            final String insertItemSql = """
                INSERT INTO dbo.TalepKalemleri (TalepId, UrunId, Miktar, TeklifFiyati)
                VALUES (?, ?, ?, ?)
                """;

            try (Connection c = DatabaseManager.getConnection()) {
                boolean oldAuto = c.getAutoCommit();
                c.setAutoCommit(false);

                int requestId = -1;
                try (PreparedStatement psHdr = c.prepareStatement(insertHeaderSql, Statement.RETURN_GENERATED_KEYS)) {
                    psHdr.setInt(1, cus.getId());
                    psHdr.executeUpdate();
                    try (ResultSet keys = psHdr.getGeneratedKeys()) {
                        if (keys.next()) {
                            requestId = ((Number) keys.getObject(1)).intValue();
                        }
                    }
                }
                if (requestId <= 0) {
                    throw new SQLException("Yeni talep Id alınamadı (generated keys).");
                }

                try (PreparedStatement psItem = c.prepareStatement(insertItemSql)) {
                    for (RequestItem it : requestItems) {
                        psItem.setInt(1, requestId);
                        psItem.setInt(2, it.getProductId());
                        psItem.setInt(3, it.getQuantity());
                        psItem.setBigDecimal(4, it.getDiscountedPrice().setScale(2, RoundingMode.HALF_UP));
                        psItem.addBatch();
                    }
                    psItem.executeBatch();
                }

                c.commit();
                try { c.setAutoCommit(oldAuto); } catch (SQLException ignore) {}

                AppDialogs.info("Talep kaydedildi.");
                if (dialogStage != null) dialogStage.close();
                else closeWindowIfPossible();

            } catch (SQLException e) {
                // Transaction esnasında hata: rollback
                AppDialogs.dbError("Talep kaydı (transaction)", e);
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
}
