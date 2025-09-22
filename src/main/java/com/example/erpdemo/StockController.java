package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/** Ürün listesi + CRUD + Geçmiş + Filtreleme (ASYNC yükleme) — stok takipsiz */
public class StockController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer>    idColumn;
    @FXML private TableColumn<Product, String>     nameColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn;
    @FXML private TableColumn<Product, String>     unitColumn;

    @FXML private TextField searchField;

    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button productHistoryButton;

    // Butonların olduğu HBox: dış tıklama filtresinde HARİÇ tutulur
    @FXML private HBox actionsBar;

    private final ObservableList<Product> master = FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<Product> filtered;

    @FXML
    public void initialize() {
        // 1) Sütun–model bağları (stok yok)
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("fiyat"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("birim"));

        // 2) Hücre hizalama/biçim
        priceColumn.setCellFactory(col -> new TableCell<>() {
            final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
            { nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2); }
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); }
                else { setText(nf.format(v)); setStyle("-fx-alignment: CENTER-RIGHT;"); }
            }
        });

        // Birim sütununu sağa hizala (istenen değişiklik)
        unitColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        productTable.setPlaceholder(new Label("Kayıtlı ürün yok"));

        // 3) Filtreleme + sıralama
        filtered = new javafx.collections.transformation.FilteredList<>(master, p -> true);
        var sorted = new javafx.collections.transformation.SortedList<>(filtered);
        sorted.comparatorProperty().bind(productTable.comparatorProperty());
        productTable.setItems(sorted);

        // 4) Arama
        searchField.textProperty().addListener((obs, old, q) -> applyFilter(q));

        // 5) Seçim yokken butonları kapat
        var noSel = productTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSel);
        deleteButton.disableProperty().bind(noSel);
        productHistoryButton.disableProperty().bind(noSel);

        // 6) Tablo içinde boş alana tıklanınca seçimi/odağı temizle
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

        // 7) Tablo DIŞINA tıklanınca seçimi/odağı temizle — actionsBar HARİÇ
        javafx.application.Platform.runLater(() -> {
            // mavi çerçeve ile açılmasın
            if (productTable.getParent() != null) productTable.getParent().requestFocus();

            Scene scene = productTable.getScene();
            if (scene == null) return;

            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable   = isChildOf(n, productTable);
                boolean insideActions = isChildOf(n, actionsBar);
                if (!insideTable && !insideActions) {
                    productTable.getSelectionModel().clearSelection();
                    if (productTable.getParent() != null) productTable.getParent().requestFocus();
                }
            });
        });

        // 8) Veriyi yükle (ASYNC)
        loadProducts();
    }

    private static boolean isChildOf(Node n, Node root) {
        if (n == null || root == null) return false;
        while (n != null) {
            if (n == root) return true;
            n = n.getParent();
        }
        return false;
    }

    private void applyFilter(String query) {
        final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) { filtered.setPredicate(p -> true); return; }

        filtered.setPredicate(p -> {
            if (contains(p.getUrunAdi(), q)) return true;
            if (contains(p.getBirim(), q)) return true;

            BigDecimal fiyat = p.getFiyat() == null ? BigDecimal.ZERO : p.getFiyat();
            if (fiyat.toPlainString().toLowerCase(Locale.ROOT).contains(q)) return true;
            String f2 = String.format(Locale.ROOT, "%.2f", fiyat);
            if (f2.contains(q)) return true;

            return String.valueOf(p.getId()).contains(q);
        });
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    /** Ürünleri arka planda yükler; UI donmaz. */
    private void loadProducts() {
        setBusy(true);
        Async.run(
                () -> {
                    try {
                        return ProductDAO.getAllProducts();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                list -> master.setAll(list),
                ex -> AppDialogs.dbError("Ürün verileri yüklenmesi", toSql(ex)),
                ()  -> setBusy(false)
        );
    }

    private void setBusy(boolean busy) {
        if (productTable != null) productTable.setDisable(busy);
        if (searchField != null)  searchField.setDisable(busy);
        if (actionsBar != null)   actionsBar.setDisable(busy);
    }

    @FXML private void handleClearSearch() { searchField.clear(); }

    /* ----------------- CRUD / Geçmiş ----------------- */

    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent root = loader.load();

            var stage = new javafx.stage.Stage();
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Ürün Ekle");
            stage.setScene(new Scene(root));
            IconUtil.setAppIcon(stage);
            stage.showAndWait();

            loadProducts(); // async
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
            Parent root = loader.load();

            NewProductController controller = loader.getController();
            controller.setProduct(sel); // aynı dialog: düzenleme

            var dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Ürün Düzenle");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            IconUtil.setAppIcon(dialogStage);
            dialogStage.showAndWait();

            loadProducts(); // async
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
            setBusy(true);
            Async.runVoid(
                    () -> {
                        try { ProductDAO.deleteProduct(sel.getId()); }
                        catch (SQLException e) { throw new RuntimeException(e); }
                    },
                    () -> { AppDialogs.info("Ürün başarıyla silindi."); loadProducts(); },
                    ex  -> AppDialogs.dbError("Ürün silme", toSql(ex)),
                    ()  -> setBusy(false)
            );
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

            var dlg = new javafx.stage.Stage();
            dlg.setTitle("Ürün Geçmişi – " + sel.getUrunAdi());
            dlg.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dlg.initOwner(productTable.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);
            dlg.showAndWait();
        } catch (IOException e) {
            AppDialogs.unexpectedError("Ürün geçmişi penceresi açma", e);
        }
    }

    private static SQLException toSql(Throwable t) {
        if (t instanceof SQLException se) return se;
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof SQLException se) return se;
            c = c.getCause();
        }
        return new SQLException(t.getMessage(), t);
    }
}
