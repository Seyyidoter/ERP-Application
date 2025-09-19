package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;

public class ProductHistoryController {

    @FXML private Label lblProduct;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtCustomer;

    @FXML private TableView<ProductHistoryRow> tblHistory;
    @FXML private TableColumn<ProductHistoryRow, Integer>   colReqId;
    @FXML private TableColumn<ProductHistoryRow, LocalDate> colDate;
    @FXML private TableColumn<ProductHistoryRow, String>    colStatus;
    @FXML private TableColumn<ProductHistoryRow, String>    colCustomer;
    @FXML private TableColumn<ProductHistoryRow, Integer>   colQty;
    @FXML private TableColumn<ProductHistoryRow, Double>    colUnit;
    @FXML private TableColumn<ProductHistoryRow, Double>    colSubtotal;

    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Product product;

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUnit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v));
                setStyle(empty ? "" : "-fx-alignment: CENTER-RIGHT;");
            }
        });
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.2f", v));
                setStyle(empty ? "" : "-fx-alignment: CENTER-RIGHT;");
            }
        });

        tblHistory.setPlaceholder(new Label("Kayıt bulunmuyor."));

        // Durum seçenekleri
        cbStatus.getItems().setAll("Hepsi", "Onaylandı", "Reddedildi", "Onay Bekliyor");
        cbStatus.getSelectionModel().selectFirst();
    }

    public void setDialogStage(Stage s) { this.dialogStage = s; }

    public void setProduct(Product p) {
        this.product = p;
        lblProduct.setText(p != null ? p.getUrunAdi() : "—");
        loadData(); // ilk yükleme
    }

    @FXML
    private void handleApplyFilters() { loadData(); }

    @FXML
    private void handleClearFilters() {
        dpFrom.setValue(null);
        dpTo.setValue(null);
        cbStatus.getSelectionModel().selectFirst();
        txtCustomer.clear();
        loadData();
    }

    private void loadData() {
        if (product == null) return;

        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();
        String status  = cbStatus.getValue();
        String statusParam = (status == null || "Hepsi".equals(status)) ? "" : status;
        String customerLike = txtCustomer.getText() == null ? "" : txtCustomer.getText().trim();

        try {
            ObservableList<ProductHistoryRow> list = ProductHistoryDAO.getProductHistory(
                    product.getId(), from, to, statusParam, customerLike
            );
            tblHistory.setItems(list);

            // Yalnızca "Onaylandı" olanların toplamı
            int totalQty = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToInt(ProductHistoryRow::getQuantity)
                    .sum();

            double totalAmount = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToDouble(ProductHistoryRow::getSubtotal)
                    .sum();

            lblTotalQty.setText(String.valueOf(totalQty));
            lblTotalAmount.setText(String.format("%.2f TL", totalAmount));

        } catch (SQLException e) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Geçmiş yüklenemedi:\n" + e.getMessage(), ButtonType.OK);
            a.setTitle("Hata"); a.setHeaderText(null);
            IconUtil.decorateAlert(a);
            a.showAndWait();
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) dialogStage.close();
        else if (tblHistory != null && tblHistory.getScene() != null) {
            tblHistory.getScene().getWindow().hide();
        }
    }
}
