package com.example.erpdemo.controller;

import com.example.erpdemo.dao.CustomerDAO;
import com.example.erpdemo.dao.ProductDAO;
import com.example.erpdemo.dao.RequestDAO;
import com.example.erpdemo.model.Customer;
import com.example.erpdemo.model.Product;
import com.example.erpdemo.model.RequestItem;
import com.example.erpdemo.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.control.ListCell;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class NewRequestController {

    @FXML private ComboBox<Customer> customerComboBox;
    @FXML private ComboBox<Product>  productComboBox;
    @FXML private TextField          quantityField;

    @FXML private TableView<RequestItem>               productTable;
    @FXML private TableColumn<RequestItem, String>     productNameColumn;
    @FXML private TableColumn<RequestItem, Integer>    quantityColumn;
    @FXML private TableColumn<RequestItem, BigDecimal> priceColumn;
    @FXML private TableColumn<RequestItem, BigDecimal> discountedPriceColumn;

    @FXML private Label totalAmountLabel;

    private final ObservableList<RequestItem> requestItems = FXCollections.observableArrayList();
    private Stage dialogStage;

    /** "Kaydet" başarılı olunca dışarıya haber vermek için. */
    private Runnable onSaved;

    /** Müşteri seçiminde geri alma yaparken uyarının tekrar açılmasını engellemek için. */
    private boolean suppressCustomerChange = false;

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

        // Boşken promptText görünsün
        installPromptOnEmpty(customerComboBox);
        installPromptOnEmpty(productComboBox);

        // Müşteri değişimi → doğrulama / sıfırlama
        customerComboBox.valueProperty().addListener((obs, oldCus, newCus) -> onCustomerChanged(oldCus, newCus));

        // Tablo sütunları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("listPrice"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        priceColumn.setCellFactory(MoneyCells.twoDecimalsTR());
        discountedPriceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        productTable.setItems(requestItems);
        productTable.setPlaceholder(new Label("Listeye henüz ürün eklenmedi."));

        // Liste değiştikçe toplamı güncelle
        requestItems.addListener((javafx.collections.ListChangeListener<RequestItem>) c -> updateTotalAmount());
    }

    /** Dışarıdan: Kaydet başarılı olursa çağrılacak aksiyonu ver. */
    public void setOnSaved(Runnable r) { this.onSaved = r; }

    /** Müşteri değiştirildiğinde liste/alanları kontrol ederek sıfırlar. */
    private void onCustomerChanged(Customer oldCus, Customer newCus) {
        // Geri alma sırasında tetiklenen değişikliği yok say
        if (suppressCustomerChange) {
            suppressCustomerChange = false;
            return;
        }
        if (Objects.equals(oldCus, newCus)) return;

        // Ürün listesi boşsa sessizce alanları sıfırla
        if (requestItems.isEmpty()) {
            clearProductInputs();
            return;
        }

        // Aksi halde onay iste
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setTitle("Müşteri Değiştir");
        confirm.setContentText("Müşteri değiştirildiğinde mevcut ürün listesi temizlenecek.\nDevam edilsin mi?");

        // Türkçe butonlar
        ButtonType evetBtn = new ButtonType("Evet", ButtonBar.ButtonData.YES);
        ButtonType hayirBtn = new ButtonType("Hayır", ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(evetBtn, hayirBtn);

        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == evetBtn) {
            requestItems.clear();
            clearProductInputs();
        } else {
            // Eski müşteriye geri dön → bu değişiklikte listener çalışmasın
            suppressCustomerChange = true;
            customerComboBox.getSelectionModel().select(oldCus);
        }
    }

    /** Ürün/miktar alanlarını sıfırlar ve prompt’ı geri getirir. */
    private void clearProductInputs() {
        productComboBox.getSelectionModel().clearSelection();
        productComboBox.setValue(null); // buttonCell prompt’ı gösterecek
        quantityField.clear();
        productComboBox.requestFocus();
    }

    /** ComboBox boşken (value=null) butonda promptText’i gösterir. */
    private static <T> void installPromptOnEmpty(ComboBox<T> combo) {
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(combo.getPromptText());
                } else {
                    StringConverter<T> conv = combo.getConverter();
                    setText(conv != null ? conv.toString(item) : String.valueOf(item));
                }
            }
        });
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

        BigDecimal discounted = price.multiply(
                BigDecimal.ONE.subtract(discountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
        );
        discounted = Money.scale2(discounted);

        requestItems.add(new RequestItem(0, 0, prd.getId(), prd.getUrunAdi(), qty, price, discounted));

        clearProductInputs();
    }

    @FXML
    private void handleSaveRequest() {
        Customer cus = customerComboBox.getSelectionModel().getSelectedItem();
        if (cus == null || requestItems.isEmpty()) {
            AppDialogs.warn("Müşteri seçin ve en az bir ürün ekleyin.");
            return;
        }

        try {
            // Güncel stoklara göre toplamlara bak (son kontrol)
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

            int requestId = RequestDAO.addRequestWithItems(cus.getId(), new ArrayList<>(requestItems));

            AppDialogs.info("Talep kaydedildi. (#" + requestId + ")");
            if (onSaved != null) {
                try { onSaved.run(); } catch (Throwable ignore) {}
            }
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

    public void setDialogStage(Stage s) {
        this.dialogStage = s;
        if (s != null) {
            // Dialog kapanırken ana pencereyi etkinleştir + odak ver (garanti)
            s.setOnHidden(e -> {
                try {
                    var owner = s.getOwner();
                    if (owner != null) owner.requestFocus();
                } catch (Throwable ignore) {}
            });
        }
    }

    private void updateTotalAmount() {
        BigDecimal total = requestItems.stream()
                .map(RequestItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmountLabel.setText(Money.fmtTRWithSymbol(Money.scale2(total)));
    }
}
