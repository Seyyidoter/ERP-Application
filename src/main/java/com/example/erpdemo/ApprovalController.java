package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Onay ekranı: bekleyen talepleri listeler, Onayla/Reddet işlemlerini yapar.
 * Onaylanınca talep toplamı kadar müşterinin bakiyesi DÜŞÜRÜLÜR (borç artar).
 */
public class ApprovalController {

    @FXML private TableView<RequestRow> pendingRequestsTable;
    @FXML private TableColumn<RequestRow, Integer> idColumn;
    @FXML private TableColumn<RequestRow, Integer> customerIdColumn;
    @FXML private TableColumn<RequestRow, LocalDate> dateColumn;
    @FXML private TableColumn<RequestRow, String> statusColumn;

    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;

    private final ObservableList<RequestRow> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        DateUtil.setDateColumnDMY(dateColumn);

        approveBtn.setDisable(true);
        rejectBtn.setDisable(true);

        pendingRequestsTable.setItems(rows);
        pendingRequestsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            boolean has = n != null;
            approveBtn.setDisable(!has);
            rejectBtn.setDisable(!has);
        });

        refresh();
    }

    @FXML
    private void handleApprove() {
        RequestRow sel = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            // 1) Talebi onayla
            RequestDAO.approveRequest(sel.getId(), HelloApplication.getLoggedInUserId());

            // 2) Toplamı al ve bakiyeyi düşür (borç artar)
            double total = RequestDAO.getRequestTotal(sel.getId());
            CustomerDAO.adjustBalance(sel.getCustomerId(), -total);

            info("Başarılı", "Talep onaylandı. Müşteri bakiyesine yansıtıldı.");
            refresh();
        } catch (SQLException ex) {
            error("Hata", "Onay işlemi başarısız: " + ex.getMessage());
        }
    }

    @FXML
    private void handleReject() {
        RequestRow sel = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            RequestDAO.rejectRequest(sel.getId(), HelloApplication.getLoggedInUserId());
            info("Bilgi", "Talep reddedildi.");
            refresh();
        } catch (SQLException ex) {
            error("Hata", "Reddetme işlemi başarısız: " + ex.getMessage());
        }
    }

    private void refresh() {
        try {
            rows.clear();
            for (Request r : RequestDAO.getPendingRequests()) {
                rows.add(new RequestRow(r.getId(), r.getCustomerId(), r.getRequestDate(), r.getStatus()));
            }
        } catch (SQLException ex) {
            error("Hata", "Veriler yüklenemedi: " + ex.getMessage());
        }
    }

    private void info(String t, String m){ Alert a=new Alert(Alert.AlertType.INFORMATION,m,ButtonType.OK);a.setHeaderText(null);a.setTitle(t);IconUtil.decorateAlert(a);a.showAndWait();}
    private void error(String t, String m){ Alert a=new Alert(Alert.AlertType.ERROR,m,ButtonType.OK);a.setHeaderText(null);a.setTitle(t);IconUtil.decorateAlert(a);a.showAndWait();}

    /** Tablo satırı modeli */
    public static class RequestRow {
        private final int id;
        private final int customerId;
        private final LocalDate requestDate;
        private final String status;

        public RequestRow(int id, int customerId, LocalDate requestDate, String status) {
            this.id = id; this.customerId = customerId; this.requestDate = requestDate; this.status = status;
        }
        public int getId() { return id; }
        public int getCustomerId() { return customerId; }
        public LocalDate getRequestDate() { return requestDate; }
        public String getStatus() { return status; }
    }

    public void setCurrentUserId(int userId) {
        HelloApplication.setLoggedInUserId(userId);
    }
}
