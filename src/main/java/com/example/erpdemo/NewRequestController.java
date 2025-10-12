package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ListCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

/** Talep oluşturma penceresi – Müşteri/Ürün asenkron yükleme + validasyon */
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

    /** Sahne kapandıysa (veya pencere görünmüyorsa) UI güncellemeyi durdurmak için. */
    private volatile boolean disposed = false;

    @FXML
    public void initialize() {
        // --- Prompt’ları buton hücresinde gösterebilmek için önce set et
        customerComboBox.setPromptText("Müşteri seçin");
        productComboBox.setPromptText("Ürün seçin");

        installPromptOnEmpty(customerComboBox);
        installPromptOnEmpty(productComboBox);

        // --- Müşteri değişimi → doğrulama / sıfırlama
        customerComboBox.valueProperty().addListener((obs, oldCus, newCus) -> onCustomerChanged(oldCus, newCus));

        // --- Tablo sütunları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("listPrice"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        priceColumn.setCellFactory(MoneyCells.twoDecimalsTR());
        discountedPriceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        productTable.setItems(requestItems);
        productTable.setPlaceholder(new Label("Listeye henüz ürün eklenmedi."));

        // --- Liste değiştikçe toplamı güncelle
        requestItems.addListener((javafx.collections.ListChangeListener<RequestItem>) c -> updateTotalAmount());

        // --- Miktar alanı: sadece rakam kabul et
        quantityField.setTextFormatter(new TextFormatter<>(onlyDigitsFilter()));
        // Enter ile hızlı ekleme (opsiyonel ama kullanışlı)
        quantityField.setOnAction(e -> handleAddProduct());

        // --- Pencere kapanınca disposed=true
        if (productTable != null && productTable.getScene() != null && productTable.getScene().getWindow() != null) {
            productTable.getScene().getWindow().setOnHidden(ev -> disposed = true);
        }

        // --- Referans verileri ASENKRON yükle
        loadReferenceDataAsync();
    }

    /** Dışarıdan: Kaydet başarılı olursa çağrılacak aksiyonu ver. */
    public void setOnSaved(Runnable r) { this.onSaved = r; }

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

    /** Referans müşteri/ürün listelerini arka planda çeker; UI donmaz. */
    private void loadReferenceDataAsync() {
        setBusy(true);
        Async.run(() -> {
                    try {
                        ObservableList<Customer> customers = CustomerDAO.getAllCustomers();
                        ObservableList<Product>  products  = ProductDAO.getAllProducts();
                        return new Object[]{customers, products};
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                payload -> {
                    if (uiDead()) return;
                    @SuppressWarnings("unchecked")
                    ObservableList<Customer> customers = (ObservableList<Customer>) payload[0];
                    @SuppressWarnings("unchecked")
                    ObservableList<Product>  products  = (ObservableList<Product>)  payload[1];

                    // Combobox item’larını ve converter’larını ata
                    customerComboBox.setItems(customers);
                    customerComboBox.setConverter(new CustomerStringConverter());
                    productComboBox.setItems(products);
                    productComboBox.setConverter(new ProductStringConverter());
                },
                ex -> {
                    if (uiDead()) return;
                    AppDialogs.dbError("Müşteri/ürün verileri yükleme", toSql(ex));
                },
                () -> {
                    if (uiDead()) return;
                    setBusy(false);
                });
    }

    /** UI hâlâ hayatta mı? (Pencere kapandıysa true döner ve UI güncellemesi yapılmaz) */
    private boolean uiDead() {
        if (disposed) return true;
        if (productTable == null) return true;
        var scene = productTable.getScene();
        if (scene == null) return true;
        var win = scene.getWindow();
        return (win == null || !win.isShowing());
    }

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
        ButtonType evetBtn  = new ButtonType("Evet", ButtonBar.ButtonData.YES);
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

        // Listedeki mevcut toplam miktar + eklenecek miktar stoktan büyük olmasın
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

        // --- Aynı ürün varsa merge et, yoksa yeni satır ekle ---
        int existingIdx = -1;
        for (int i = 0; i < requestItems.size(); i++) {
            if (requestItems.get(i).getProductId() == prd.getId()) {
                existingIdx = i;
                break;
            }
        }

        if (existingIdx >= 0) {
            RequestItem old = requestItems.get(existingIdx);
            int newQty = old.getQuantity() + qty;
            // (stok zaten üstte kontrol edildi)
            RequestItem merged = new RequestItem(
                    0, 0,
                    prd.getId(),
                    prd.getUrunAdi(),
                    newQty,
                    price,
                    discounted
            );
            requestItems.set(existingIdx, merged);
        } else {
            requestItems.add(new RequestItem(
                    0, 0, prd.getId(), prd.getUrunAdi(), qty, price, discounted
            ));
        }

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
                disposed = true;
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

    // --------- Yardımcılar ---------

    private static UnaryOperator<TextFormatter.Change> onlyDigitsFilter() {
        return change -> change.getControlNewText().matches("\\d*") ? change : null;
    }

    private static class CustomerStringConverter extends StringConverter<Customer> {
        @Override public String toString(Customer c) { return c == null ? "" : c.getCompanyName(); }
        @Override public Customer fromString(String s) { return null; }
    }

    private static class ProductStringConverter extends StringConverter<Product> {
        @Override public String toString(Product p) { return p == null ? "" : p.getUrunAdi(); }
        @Override public Product fromString(String s) { return null; }
    }

    /** Throwable → SQLException dönüştürücü (zincirde varsa onu döndürür) */
    private static SQLException toSql(Throwable t) {
        if (t instanceof SQLException se) return se;
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof SQLException se) return se;
            c = c.getCause();
        }
        return new SQLException(t.getMessage(), t);
    }

    /** Formu geçici kilitle (başlıca combobox'lar ve miktar alanı) */
    private void setBusy(boolean busy) {
        if (customerComboBox != null) customerComboBox.setDisable(busy);
        if (productComboBox  != null) productComboBox.setDisable(busy);
        if (quantityField    != null) quantityField.setDisable(busy);
        if (productTable     != null) productTable.setDisable(busy);
    }
}
