package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

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
    @FXML private TableColumn<ProductHistoryRow, BigDecimal> colUnit;
    @FXML private TableColumn<ProductHistoryRow, BigDecimal> colSubtotal;

    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Product product;

    private volatile boolean disposed = false;

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("qty"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUnit.setCellFactory(MoneyCells.twoDecimalsTR());
        colSubtotal.setCellFactory(MoneyCells.twoDecimalsTR());

        tblHistory.setPlaceholder(new Label("Kayıt bulunmuyor."));
        cbStatus.getItems().setAll("Hepsi", "Onaylandı", "Reddedildi", "Onay Bekliyor");
        cbStatus.getSelectionModel().selectFirst();

        try { DateUtil.setDateColumnDMY(colDate); } catch (Throwable ignore) {}

        // pencere dışından da kapatılmış olabilir – kapanınca disposed
        tblHistory.sceneProperty().addListener((o, os, ns) -> {
            if (ns != null) {
                if (ns.getWindow() != null) {
                    ns.getWindow().setOnHidden(e -> disposed = true);
                } else {
                    ns.windowProperty().addListener((oo, ow, nw) -> {
                        if (nw != null) nw.setOnHidden(e -> disposed = true);
                    });
                }
            }
        });
    }

    private boolean uiDead() {
        if (disposed) return true;
        if (tblHistory == null) return true;
        var scene = tblHistory.getScene();
        if (scene == null) return true;
        var win = scene.getWindow();
        return (win == null || !win.isShowing());
    }

    public void setDialogStage(Stage s) {
        this.dialogStage = s;
        if (this.dialogStage != null) {
            this.dialogStage.setOnHidden(e -> disposed = true);
        }
    }

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

        setBusy(true);
        String finalCustomerLike = customerLike;

        Async.run(() -> {
                    try {
                        return ProductHistoryDAO.findHistoryForProduct(
                                product.getId(), from, to, statusParam, finalCustomerLike);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                list -> {
                    if (uiDead()) return;
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
                    lblTotalAmount.setText(Money.fmtTRWithSymbol(totalAmount));
                },
                ex -> {
                    if (uiDead()) return;
                    AppDialogs.dbError("Ürün geçmişi yükleme", toSql(ex));
                },
                () -> {
                    if (uiDead()) return;
                    setBusy(false);
                });
    }

    private void setBusy(boolean busy) {
        if (tblHistory != null) tblHistory.setDisable(busy);
        if (dialogStage != null && dialogStage.getScene() != null) {
            dialogStage.getScene().getRoot().setDisable(busy);
        }
    }

    @FXML
    private void handleClose() {
        disposed = true;
        if (dialogStage != null) dialogStage.close();
        else if (tblHistory != null && tblHistory.getScene() != null) {
            tblHistory.getScene().getWindow().hide();
        }
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
}
