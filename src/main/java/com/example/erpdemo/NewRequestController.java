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
import java.util.*;
import java.util.function.UnaryOperator;
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

        // Sütun–model bağları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("listPrice"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        // Hücre stil/biçimleri
        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        priceColumn.setCellFactory(MoneyCells.twoDecimalsTR());
        discountedPriceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        productTable.setItems(requestItems);
        productTable.setPlaceholder(new Label("Listeye henüz ürün eklenmedi."));

        // Toplam, liste değiştiğinde güncellensin
        requestItems.addListener((javafx.collections.ListChangeListener<RequestItem>) c -> updateTotalAmount());

        // 🔹 MİKTAR alanı: yalnızca rakam (boş da serbest – kullanıcı yazarken)
        quantityField.setTextFormatter(new TextFormatter<>(numericIntFilter()));

        // 🔹 Enter ile ekleme
        quantityField.setOnAction(e -> handleAddProduct());

        // Açılışta toplam etiketi güvenli biçimde güncelle
        updateTotalAmount();
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

        if (cus == null || prd == null || quantityField.getText() == null || quantityField.getText().trim().isBlank()) {
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

        BigDecimal price = prd.getFiyat() != null ? prd.getFiyat() : BigDecimal.ZERO; // liste fiyat
        BigDecimal discountPct = BigDecimal.valueOf(cus.getIskonto());                 // % int

        BigDecimal discounted = price
                .multiply(BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        discounted = Money.scale2(discounted); // 🔸 Tek noktadan 2 ondalık

        requestItems.add(new RequestItem(0, 0, prd.getId(), prd.getUrunAdi(), qty, price, discounted));

        // İlk kalem eklendiyse müşteri değişmesin (iskonto tutarlılığı)
        if (!requestItems.isEmpty()) {
            customerComboBox.setDisable(true);
        }

        // UX temizlikleri
        quantityField.clear();
        productComboBox.getSelectionModel().clearSelection();
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

            // 2) Kayıt: Controller değil DAO yapsın (tek noktadan)
            int requestId = RequestDAO.addRequestWithItems(cus.getId(), new ArrayList<>(requestItems));

            AppDialogs.info("Talep kaydedildi. (#" + requestId + ")");
            if (dialogStage != null) dialogStage.close();
            else closeWindowIfPossible();

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
        totalAmountLabel.setText(Money.fmtTRWithSymbol(Money.scale2(total))); // ör: "₺1.234,56"
    }

    /** Yalnızca 0-9 (boş’a izin ver, silerken engel olmasın). */
    private static UnaryOperator<TextFormatter.Change> numericIntFilter() {
        return change -> {
            String newText = change.getControlNewText();
            return newText.matches("\\d*") ? change : null;
        };
    }
}
