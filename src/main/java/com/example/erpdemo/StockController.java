package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/** Ürün listesi + CRUD + Geçmiş + Filtreleme */
public class StockController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer>    idColumn;
    @FXML private TableColumn<Product, String>     nameColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn;
    @FXML private TableColumn<Product, Integer>    stockColumn;
    @FXML private TableColumn<Product, String>     unitColumn;

    @FXML private TextField searchField;

    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button productHistoryButton;

    private final ObservableList<Product> master = FXCollections.observableArrayList();
    private FilteredList<Product> filtered;

    @FXML
    public void initialize() {
        // 1) Sütun–model bağları
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("fiyat"));   // BigDecimal
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stok"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("birim"));

        // 2) Hizalama + TR para biçimlendirme
        stockColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        priceColumn.setCellFactory(col -> new TableCell<>() {
            final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
            { nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2); }
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); }
                else { setText(nf.format(v)); setStyle("-fx-alignment: CENTER-RIGHT;"); }
            }
        });

        // 3) Boş tablo mesajı
        productTable.setPlaceholder(new Label("Kayıtlı ürün yok"));

        // 4) Filtreleme + sıralama hattı
        filtered = new FilteredList<>(master, p -> true);
        SortedList<Product> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(productTable.comparatorProperty());
        productTable.setItems(sorted);

        // 5) Arama kutusu
        searchField.textProperty().addListener((obs, old, q) -> applyFilter(q));

        // 6) Seçim yokken butonları pasifleştir
        var noSel = productTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSel);
        deleteButton.disableProperty().bind(noSel);
        productHistoryButton.disableProperty().bind(noSel);

        // 7) TABLO İÇİNDE boş alana tıklanınca seçimi temizle (mavi çerçeveyi de kaldır)
        productTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    productTable.getSelectionModel().clearSelection();
                    if (productTable.getParent() != null) productTable.getParent().requestFocus();
                }
            });
            return row;
        });

        // 8) TABLO DIŞINDA herhangi bir yere tıklanınca seçimi/odakı temizle
        javafx.application.Platform.runLater(() -> {
            var scene = productTable.getScene();
            if (scene == null) return; // güvenlik

            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                javafx.scene.Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable = false;
                while (n != null) {
                    if (n == productTable) { insideTable = true; break; }
                    n = n.getParent();
                }
                if (!insideTable) {
                    productTable.getSelectionModel().clearSelection();
                    if (productTable.getParent() != null) productTable.getParent().requestFocus();
                }
            });
        });

        // 9) Veriyi yükle
        loadProducts();
    }



    private void applyFilter(String query) {
        final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { filtered.setPredicate(p -> true); return; }

        filtered.setPredicate(p -> {
            if (contains(p.getUrunAdi(), q)) return true;
            if (contains(p.getBirim(), q)) return true;

            // fiyat & stok serbest metin eşleşmesi
            BigDecimal fiyat = p.getFiyat() == null ? BigDecimal.ZERO : p.getFiyat();
            if (fiyat.toPlainString().toLowerCase(Locale.ROOT).contains(q)) return true;
            String f2 = String.format(Locale.ROOT, "%.2f", fiyat);
            if (f2.contains(q)) return true;

            if (String.valueOf(p.getStok()).contains(q)) return true;
            if (String.valueOf(p.getId()).contains(q)) return true;

            return false;
        });
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private void loadProducts() {
        try {
            master.setAll(ProductDAO.getAllProducts());
        } catch (SQLException e) {
            AppDialogs.dbError("Ürün verileri yüklenmesi", e);
        }
    }

    @FXML private void handleClearSearch() { searchField.clear(); }

    /* ----------------- mevcut CRUD/Geçmiş aksiyonları ----------------- */

    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent parent = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Ürün Ekle");
            stage.setScene(new Scene(parent));
            IconUtil.setAppIcon(stage);
            stage.showAndWait();

            loadProducts();
        } catch (IOException e) {
            AppDialogs.unexpectedError("Yeni ürün penceresi açma", e);
        }
    }

    @FXML
    private void handleEditButton() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent parent = loader.load();

            NewProductController controller = loader.getController();
            controller.setProduct(sel); // aynı diyalog: düzenleme

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Ürün Düzenle");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(parent));
            IconUtil.setAppIcon(dialogStage);
            dialogStage.showAndWait();

            loadProducts();
        } catch (IOException e) {
            AppDialogs.unexpectedError("Ürün düzenleme penceresi açma", e);
        }
    }

    @FXML
    private void handleDeleteButton() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Ürünü silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);
        confirm.showAndWait();

        if (confirm.getResult() == ButtonType.YES) {
            try {
                ProductDAO.deleteProduct(sel.getId());
                AppDialogs.info("Ürün başarıyla silindi.");
                loadProducts();
            } catch (SQLException e) {
                AppDialogs.dbError("Ürün silme", e);
            }
        }
    }

    @FXML
    private void handleProductHistory() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("product-history-view.fxml"));
            Parent view = loader.load();

            ProductHistoryController c = loader.getController();
            c.setProduct(sel);

            Stage dlg = new Stage();
            dlg.setTitle("Ürün Geçmişi – " + sel.getUrunAdi());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(productTable.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);
            dlg.showAndWait();
        } catch (IOException e) {
            AppDialogs.unexpectedError("Ürün geçmişi penceresi açma", e);
        }
    }
}
