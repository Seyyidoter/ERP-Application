package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class StockController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String>  nameColumn;
    @FXML private TableColumn<Product, Double>  priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, String>  unitColumn;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("fiyat"));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stok"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("birim"));

        // --- UI dokunuşu: sayı/para biçimlendirme + hizalama ---
        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v));
                setStyle(empty ? "" : "-fx-alignment: CENTER-RIGHT;");
            }
        });
        stockColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.valueOf(v));
                setStyle(empty ? "" : "-fx-alignment: CENTER-RIGHT;");
            }
        });
        unitColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        loadProducts();
    }

    private void loadProducts() {
        try {
            ObservableList<Product> productList = ProductDAO.getAllProducts();
            productTable.setItems(productList);
        } catch (SQLException e) {
            e.printStackTrace();
            showInfo("Hata", "Ürün verileri yüklenirken bir hata oluştu.");
        }
    }

    // --------- Ekle ----------
    @FXML
    private void handleAddButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent parent = loader.load();
            NewProductController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Ürün Ekle");
            stage.setScene(new Scene(parent));
            IconUtil.setAppIcon(stage);

            controller.setDialogStage(stage);
            stage.showAndWait();
            loadProducts();
        } catch (IOException e) {
            e.printStackTrace();
            showInfo("Hata", "Yeni ürün penceresi açılamadı.");
        }
    }

    // --------- Düzenle ----------
    @FXML
    private void handleEditButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct == null) { showInfo("Uyarı", "Lütfen düzenlemek için bir ürün seçin."); return; }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent parent = loader.load();
            NewProductController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Ürün Düzenle");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(parent));
            IconUtil.setAppIcon(dialogStage);

            controller.setDialogStage(dialogStage);
            controller.setProduct(selectedProduct);

            dialogStage.showAndWait();
            loadProducts();
        } catch (IOException e) {
            e.printStackTrace();
            showInfo("Hata", "Ürün düzenleme penceresi açılamadı.");
        }
    }

    // --------- Sil ----------
    @FXML
    private void handleDeleteButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct == null) { showInfo("Uyarı", "Lütfen silmek için bir ürün seçin."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Ürün silinecek. Emin misiniz?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    ProductDAO.deleteProduct(selectedProduct.getId());
                    showInfo("Başarılı", "Ürün başarıyla silindi.");
                    loadProducts();
                } catch (SQLException e) {
                    e.printStackTrace();
                    String msg = e.getMessage();
                    if (msg != null && msg.toLowerCase().contains("foreign key")) {
                        showInfo("Hata", "Bu ürün taleplerde kullanılıyor olabilir. Önce ilişkili kayıtları temizleyin.");
                    } else {
                        showInfo("Hata", "Ürün silinirken bir hata oluştu: " + e.getMessage());
                    }
                }
            }
        });
    }

    // --------- Ürün Geçmişi ----------
    @FXML
    private void handleProductHistory() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Lütfen geçmişini görmek istediğiniz ürünü seçin.", ButtonType.OK);
            a.setTitle("Uyarı");
            a.setHeaderText(null);
            IconUtil.decorateAlert(a);
            a.showAndWait();
            return;
        }

        try {
            var url = getClass().getResource("product-history-view.fxml");
            if (url == null) throw new IllegalStateException("product-history-view.fxml bulunamadı.");
            FXMLLoader loader = new FXMLLoader(url);
            Parent view = loader.load();

            ProductHistoryController controller = loader.getController();

            Stage dlg = new Stage();
            dlg.setTitle("Ürün Geçmişi – " + sel.getUrunAdi());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(productTable.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);

            controller.setDialogStage(dlg);
            controller.setProduct(sel);

            dlg.showAndWait();
        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Geçmiş penceresi açılamadı:\n" + ex.getMessage(), ButtonType.OK);
            a.setTitle("Hata");
            a.setHeaderText(null);
            IconUtil.decorateAlert(a);
            a.showAndWait();
        }
    }

    // --------- Yardımcı ----------
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);
        alert.showAndWait();
    }
}
