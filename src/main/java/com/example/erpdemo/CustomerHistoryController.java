package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Müşteri geçmişi ekranı – TÜM para görüntülemeleri BigDecimal + TR format.
 * (double'a dönüşüm YOK)
 */
public class CustomerHistoryController {

    // --- Filtre alanları
    @FXML private Label lblCustomer;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtProduct;

    // --- Tablo ve kolonlar
    @FXML private TableView<CustomerHistoryRow> tblHistory;
    @FXML private TableColumn<CustomerHistoryRow, Integer>    colReqId;
    @FXML private TableColumn<CustomerHistoryRow, LocalDate>  colDate;
    @FXML private TableColumn<CustomerHistoryRow, String>     colStatus;
    @FXML private TableColumn<CustomerHistoryRow, String>     colProduct;
    @FXML private TableColumn<CustomerHistoryRow, Integer>    colQty;
    @FXML private TableColumn<CustomerHistoryRow, BigDecimal> colUnit;
    @FXML private TableColumn<CustomerHistoryRow, BigDecimal> colSubtotal;

    // --- Toplam etiketleri
    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Customer customer;

    @FXML
    public void initialize() {
        // Model bağları (getter isimlerine bire bir karşılık)
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        // Görünüm: hizalama + para biçimlendirme (TR)
        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUnit.setCellFactory(MoneyCells.twoDecimalsTR());
        colSubtotal.setCellFactory(MoneyCells.twoDecimalsTR());

        tblHistory.setPlaceholder(new Label("Kayıt bulunmuyor."));
        cbStatus.getItems().setAll("Hepsi", "Onaylandı", "Reddedildi", "Onay Bekliyor");
        cbStatus.getSelectionModel().selectFirst();

        try { DateUtil.setDateColumnDMY(colDate); } catch (Throwable ignore) {}
    }

    public void setDialogStage(Stage s) { this.dialogStage = s; }

    public void setCustomer(Customer c) {
        this.customer = c;
        lblCustomer.setText(c != null ? c.getCompanyName() : "—"); // changed
        loadData();
    }

    @FXML private void handleApplyFilters() { loadData(); }

    @FXML
    private void handleClearFilters() {
        dpFrom.setValue(null);
        dpTo.setValue(null);
        cbStatus.getSelectionModel().selectFirst();
        txtProduct.clear();
        loadData();
    }

    private void loadData() {
        if (customer == null) return;

        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();
        String status  = cbStatus.getValue();
        String statusParam = ("Hepsi".equals(status) ? null : status);
        String productLike = txtProduct.getText() == null ? null : txtProduct.getText().trim();
        if (productLike != null && productLike.isEmpty()) productLike = null;

        setBusy(true);
        final String finalProductLike = productLike;

        Async.run(() -> {
                    try {
                        // Müşteri bazlı geçmiş; tarih aralığı CASTsız, indeks dostu.
                        return CustomerHistoryDAO.findHistoryForCustomer(
                                customer.getId(), from, to, statusParam, finalProductLike);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                this::applyResultToView,
                ex -> AppDialogs.dbError("Müşteri geçmişi yükleme", toSql(ex)),
                () -> setBusy(false));
    }

    private void applyResultToView(ObservableList<CustomerHistoryRow> list) {
        tblHistory.setItems(list);

        // SADECE BigDecimal ile çalış: double YOK
        int totalQty = list.stream()
                .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                .mapToInt(CustomerHistoryRow::getQuantity) // changed
                .sum();

        BigDecimal totalAmount = list.stream()
                .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                .map(CustomerHistoryRow::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotalQty.setText(String.valueOf(totalQty));
        lblTotalAmount.setText(MoneyCells.fmtTL(totalAmount)); // TR, 2 ondalık
    }

    private void setBusy(boolean busy) {
        tblHistory.setDisable(busy);
        if (dialogStage != null) dialogStage.getScene().getRoot().setDisable(busy);
    }

    @FXML
    private void handleClose() {
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
