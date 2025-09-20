package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class CustomerHistoryController {

    @FXML private Label lblCustomer;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtProduct;

    @FXML private TableView<CustomerHistoryRow> tblHistory;
    @FXML private TableColumn<CustomerHistoryRow, Integer>    colReqId;
    @FXML private TableColumn<CustomerHistoryRow, LocalDate>  colDate;
    @FXML private TableColumn<CustomerHistoryRow, String>     colStatus;
    @FXML private TableColumn<CustomerHistoryRow, String>     colProduct;
    @FXML private TableColumn<CustomerHistoryRow, Integer>    colQty;
    @FXML private TableColumn<CustomerHistoryRow, BigDecimal> colUnit;      // BigDecimal
    @FXML private TableColumn<CustomerHistoryRow, BigDecimal> colSubtotal;  // BigDecimal

    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Customer customer;

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        // Tarih formatı (DateUtil yoksa)
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDate v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                } else {
                    setText(v.format(DMY));
                }
            }
        });

        // Sayısal hizalama
        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");

        // BigDecimal güvenli yazdırma
        colUnit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                } else {
                    // İki ondalık, Türkçe nokta/virgül için Locale kullanımı
                    BigDecimal scaled = v.setScale(2, RoundingMode.HALF_UP);
                    setText(String.format(TR, "%.2f", scaled.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                } else {
                    BigDecimal scaled = v.setScale(2, RoundingMode.HALF_UP);
                    setText(String.format(TR, "%.2f", scaled.doubleValue()));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        tblHistory.setPlaceholder(new Label("Kayıt bulunmuyor."));
        cbStatus.getItems().setAll("Hepsi", "Onaylandı", "Reddedildi", "Onay Bekliyor");
        cbStatus.getSelectionModel().selectFirst();
    }

    public void setDialogStage(Stage s) { this.dialogStage = s; }

    public void setCustomer(Customer c) {
        this.customer = c;
        lblCustomer.setText(c != null ? c.getCompanyName() : "—");
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
        String sParam  = ("Hepsi".equals(status) ? null : status);
        String pLike   = txtProduct.getText();
        if (pLike != null) pLike = pLike.trim();
        if (pLike != null && pLike.isEmpty()) pLike = null;

        try {
            // DAO metod adı: findHistoryForCustomer
            ObservableList<CustomerHistoryRow> list =
                    CustomerHistoryDAO.findHistoryForCustomer(customer.getId(), from, to, sParam, pLike);

            tblHistory.setItems(list);

            int totalQty = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToInt(CustomerHistoryRow::getQuantity)
                    .sum();

            BigDecimal totalAmount = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .map(CustomerHistoryRow::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            lblTotalQty.setText(String.valueOf(totalQty));
            lblTotalAmount.setText(String.format(TR, "%.2f TL", totalAmount.doubleValue()));

        } catch (SQLException e) {
            AppDialogs.dbError("Müşteri geçmişi yükleme", e);
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
