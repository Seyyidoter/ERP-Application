package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Onay ekranı: bekleyen talepleri listeler, Onayla/Reddet işlemlerini yapar. */
public class ApprovalController {

    @FXML private TableView<RequestRow> pendingRequestsTable;
    @FXML private TableColumn<RequestRow, Integer>   idColumn;
    @FXML private TableColumn<RequestRow, Integer>   customerIdColumn;
    @FXML private TableColumn<RequestRow, LocalDate> dateColumn;
    @FXML private TableColumn<RequestRow, String>    statusColumn;

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

        setBusy(true);
        Async.runVoid(() -> {
                    try {
                        int approverId = HelloApplication.getLoggedInUserId();
                        RequestDAO.approveRequestTransactionally(sel.getId(), approverId);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, () -> {
                    AppDialogs.info("Talep onaylandı. Stok ve müşteri bakiyesi güncellendi.");
                    refresh();
                }, ex -> AppDialogs.dbError("Talep onaylama", toSql(ex)),
                () -> setBusy(false));
    }

    @FXML
    private void handleReject() {
        RequestRow sel = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        setBusy(true);
        Async.runVoid(() -> {
                    try {
                        RequestDAO.rejectRequest(sel.getId(), HelloApplication.getLoggedInUserId());
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, () -> {
                    AppDialogs.info("Talep reddedildi.");
                    refresh();
                }, ex -> AppDialogs.dbError("Talep reddetme", toSql(ex)),
                () -> setBusy(false));
    }

    private void refresh() {
        setBusy(true);
        Async.run(() -> {
                    try {
                        List<Request> list = RequestDAO.getPendingRequests();
                        ObservableList<RequestRow> tmp = FXCollections.observableArrayList();
                        for (Request r : list) tmp.add(new RequestRow(r.getId(), r.getCustomerId(), r.getRequestDate(), r.getStatus()));
                        return tmp;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, tmp -> {
                    rows.setAll(tmp);
                    pendingRequestsTable.getSelectionModel().clearSelection();
                }, ex -> AppDialogs.dbError("Bekleyen taleplerin yüklenmesi", toSql(ex)),
                () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        pendingRequestsTable.setDisable(busy);
        approveBtn.setDisable(busy || pendingRequestsTable.getSelectionModel().getSelectedItem() == null);
        rejectBtn.setDisable(busy || pendingRequestsTable.getSelectionModel().getSelectedItem() == null);
    }

    private static SQLException toSql(Throwable t) {
        if (t instanceof SQLException se) return se;
        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (c instanceof SQLException se) return se;
            c = c.getCause();
        }
        return new SQLException(t.getMessage(), t);
    }

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

    public void setCurrentUserId(int userId) { HelloApplication.setLoggedInUserId(userId); }
}
