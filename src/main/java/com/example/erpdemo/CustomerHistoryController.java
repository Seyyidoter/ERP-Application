package com.example.erpdemo;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDate;

public class CustomerHistoryController {

    @FXML private Label lblCustomer;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TextField txtProduct;

    @FXML private TableView<CustomerHistoryRow> tblHistory;
    @FXML private TableColumn<CustomerHistoryRow, Integer>   colReqId;
    @FXML private TableColumn<CustomerHistoryRow, LocalDate> colDate;
    @FXML private TableColumn<CustomerHistoryRow, String>    colStatus;
    @FXML private TableColumn<CustomerHistoryRow, String>    colProduct;
    @FXML private TableColumn<CustomerHistoryRow, Integer>   colQty;
    @FXML private TableColumn<CustomerHistoryRow, Double>    colUnit;
    @FXML private TableColumn<CustomerHistoryRow, Double>    colSubtotal;

    @FXML private Label lblTotalQty, lblTotalAmount;

    private Stage dialogStage;
    private Customer customer;

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
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
        String sParam  = (status == null || "Hepsi".equals(status)) ? "" : status;
        String pLike   = txtProduct.getText() == null ? "" : txtProduct.getText().trim();

        try {
            ObservableList<CustomerHistoryRow> list = CustomerHistoryDAO.getCustomerHistory(
                    customer.getId(), from, to, sParam, pLike);

            tblHistory.setItems(list);

            // Yalnızca "Onaylandı" olanların toplamı
            int totalQty = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToInt(CustomerHistoryRow::getQuantity)
                    .sum();

            double totalAmount = list.stream()
                    .filter(r -> "Onaylandı".equalsIgnoreCase(r.getStatus()))
                    .mapToDouble(CustomerHistoryRow::getSubtotal)
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
