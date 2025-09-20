package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;

public class ProductHistoryController {

    @FXML private Label lblProduct;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtCustomer;

    @FXML private TableView<ProductHistoryRow> tblHistory;
    @FXML private TableColumn<ProductHistoryRow, Integer>    colReqId;
    @FXML private TableColumn<ProductHistoryRow, LocalDate>  colDate;
    @FXML private TableColumn<ProductHistoryRow, String>     colStatus;
    @FXML private TableColumn<ProductHistoryRow, String>     colCustomer;
    @FXML private TableColumn<ProductHistoryRow, Integer>    colQty;
    @FXML private TableColumn<ProductHistoryRow, BigDecimal> colUnit;      // BigDecimal
    @FXML private TableColumn<ProductHistoryRow, BigDecimal> colSubtotal;  // BigDecimal

    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Product product;

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));   // <-- customerName yerine customer
        colQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        // hizalama + sayı biçimleme
        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUnit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null); setStyle("");
                } else {
                    setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f", v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null); setStyle("");
                } else {
                    setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f", v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        tblHistory.setPlaceholder(new Label("Kayıt bulunmuyor."));
        cbStatus.getItems().setAll("Hepsi", "Onaylandı", "Reddedildi", "Onay Bekliyor");
        cbStatus.getSelectionModel().selectFirst();

        // tarih kolonu görsel formatı (sende varsa)
        try { DateUtil.setDateColumnDMY(colDate); } catch (Throwable ignore) {}
    }

    public void setDialogStage(Stage s) { this.dialogStage = s; }

    public void setProduct(Product p) {
        this.product = p;
        lblProduct.setText(p != null ? p.getUrunAdi() : "—");
        loadData();
    }

    @FXML private void handleApplyFilters() { loadData(); }

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
        String statusParam = ("Hepsi".equals(status) ? null : status);
        String customerLike = txtCustomer.getText() == null ? null : txtCustomer.getText().trim();
        if (customerLike != null && customerLike.isEmpty()) customerLike = null;

        try {
            ObservableList<ProductHistoryRow> list =
                    ProductHistoryDAO.findHistoryForProduct(product.getId(), from, to, statusParam, customerLike);
            tblHistory.setItems(list);

            int totalQty = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToInt(ProductHistoryRow::getQty)
                    .sum();

            BigDecimal totalAmount = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .map(ProductHistoryRow::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            lblTotalQty.setText(String.valueOf(totalQty));
            lblTotalAmount.setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f TL", totalAmount));

        } catch (SQLException e) {
            AppDialogs.dbError("Ürün geçmişi yükleme", e);
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
