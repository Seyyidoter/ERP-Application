package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

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

        // --- UI dokunuşu: para/sayı biçimlendirme + hizalama ---
        priceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v));
                setStyle(empty ? "" : "-fx-alignment: CENTER-RIGHT;");
            }
        });
        unitColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        stockColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        loadProducts();
    }

    private void loadProducts() {
        try {
            ObservableList<Product> productList = ProductDAO.getAllProducts();
            productTable.setItems(productList);
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Hata", "Ürün verileri yüklenirken bir hata oluştu.");
        }
    }

    @FXML
    private void handleAddButton() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("new-product.fxml"));
            javafx.scene.Parent parent = loader.load();
            NewProductController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Ürün Ekle");
            stage.setScene(new javafx.scene.Scene(parent));
            IconUtil.setAppIcon(stage);

            controller.setDialogStage(stage);
            stage.showAndWait();
            loadProducts();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Hata", "Yeni ürün penceresi açılamadı.");
        }
    }

    @FXML
    private void handleEditButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct != null) {
            try {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("new-product.fxml"));
                javafx.scene.Parent parent = loader.load();
                NewProductController controller = loader.getController();

                Stage dialogStage = new Stage();
                dialogStage.setTitle("Ürün Düzenle");
                dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                dialogStage.setScene(new javafx.scene.Scene(parent));
                IconUtil.setAppIcon(dialogStage);

                controller.setDialogStage(dialogStage);
                controller.setProduct(selectedProduct);

                dialogStage.showAndWait();
                loadProducts();
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Hata", "Ürün düzenleme penceresi açılamadı.");
            }
        } else {
            showAlert("Uyarı", "Lütfen düzenlemek için bir ürün seçin.");
        }
    }

    @FXML
    private void handleDeleteButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct != null) {
            try {
                ProductDAO.deleteProduct(selectedProduct.getId());
                showAlert("Başarılı", "Ürün başarıyla silindi.");
                loadProducts();
            } catch (SQLException e) {
                e.printStackTrace();
                // --- UI dokunuşu: FK hatasında kullanıcı dostu mesaj ---
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().contains("foreign key")) {
                    showAlert("Hata", "Kayıt başka veriler tarafından kullanılıyor (FK). Önce ilişkili kayıtları silin.");
                } else {
                    showAlert("Hata", "Ürün silinirken bir hata oluştu: " + e.getMessage());
                }
            }
        } else {
            showAlert("Uyarı", "Lütfen silmek için bir ürün seçin.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        IconUtil.decorateAlert(alert);
        alert.showAndWait();
    }
}
