package com.example.erpdemo;

import javafx.collections.FXCollections;
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
import java.util.Locale;

public class StockController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer>    idColumn;
    @FXML private TableColumn<Product, String>     nameColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn; // BigDecimal
    @FXML private TableColumn<Product, Integer>    stockColumn;
    @FXML private TableColumn<Product, String>     unitColumn;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("fiyat"));   // BigDecimal
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stok"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("birim"));

        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); }
                else {
                    setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f", v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
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
            var productList = ProductDAO.getAllProducts();
            productTable.setItems(FXCollections.observableArrayList(productList));
        } catch (SQLException e) {
            AppDialogs.dbError("Ürün verileri yüklenmesi", e);
        }
    }

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
            AppDialogs.unexpectedError("Yeni ürün penceresi açma", e);
        }
    }

    @FXML
    private void handleEditButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct == null) { AppDialogs.warn("Lütfen düzenlemek için bir ürün seçin."); return; }

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
            AppDialogs.unexpectedError("Ürün düzenleme penceresi açma", e);
        }
    }

    @FXML
    private void handleDeleteButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct == null) { AppDialogs.warn("Lütfen silmek için bir ürün seçin."); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Ürün silinecek. Emin misiniz?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Onay");
        IconUtil.decorateAlert(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    ProductDAO.deleteProduct(selectedProduct.getId());
                    AppDialogs.info("Ürün başarıyla silindi.");
                    loadProducts();
                } catch (SQLException e) {
                    AppDialogs.dbError("Ürün silme", e);
                }
            }
        });
    }

    @FXML
    private void handleProductHistory() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) { AppDialogs.warn("Lütfen geçmişini görmek istediğiniz ürünü seçin."); return; }

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
            AppDialogs.unexpectedError("Geçmiş penceresi açma", ex);
        }
    }
}
