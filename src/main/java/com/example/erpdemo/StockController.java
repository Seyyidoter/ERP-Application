package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;

public class StockController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, String> unitColumn;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("fiyat"));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stok"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("birim"));

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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
            Parent parent = loader.load();

            NewProductController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Yeni Ürün Ekle");
            stage.setScene(new Scene(parent));

            controller.setDialogStage(stage);

            stage.showAndWait();

            loadProducts();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Hata", "Yeni ürün penceresi açılamadı.");
        }
    }

    @FXML
    private void handleEditButton() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("new-product.fxml"));
                Parent parent = loader.load();

                NewProductController controller = loader.getController();

                Stage dialogStage = new Stage();
                dialogStage.setTitle("Ürün Düzenle");
                dialogStage.initModality(Modality.APPLICATION_MODAL);
                dialogStage.setScene(new Scene(parent));

                controller.setDialogStage(dialogStage);
                controller.setProduct(selectedProduct);

                dialogStage.showAndWait();

                loadProducts();
            } catch (IOException e) {
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
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Ürünü silmek istediğinizden emin misiniz?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            confirm.showAndWait();

            if (confirm.getResult() == ButtonType.YES) {
                try {
                    ProductDAO.deleteProduct(selectedProduct.getId());
                    showAlert("Başarılı", "Ürün başarıyla silindi.");
                    loadProducts();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert("Hata", "Ürün silinirken bir hata oluştu: " + e.getMessage());
                }
            }
        } else {
            showAlert("Uyarı", "Lütfen silmek için bir ürün seçin.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}