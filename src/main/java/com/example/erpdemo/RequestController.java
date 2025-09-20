package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

public class RequestController {

    @FXML private TableView<Row> tblRequests;
    @FXML private TableColumn<Row, Integer>   colId;
    @FXML private TableColumn<Row, Integer>   colCustomer;
    @FXML private TableColumn<Row, String>    colCustomerName;
    @FXML private TableColumn<Row, LocalDate> colDate;
    @FXML private TableColumn<Row, String>    colStatus;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        colCustomerName.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        DateUtil.setDateColumnDMY(colDate);

        tblRequests.setItems(rows);
        refresh();
    }

    @FXML
    private void createRequest() {
        try {
            FXMLLoader fxml = new FXMLLoader(getClass().getResource("new-request.fxml"));
            Parent view = fxml.load();

            Stage dlg = new Stage();
            dlg.setTitle("Yeni Talep");
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(tblRequests.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);
            dlg.showAndWait();

            refresh();
        } catch (IOException ex) {
            AppDialogs.unexpectedError("Talep oluşturma penceresi açma", ex);
        }
    }

    @FXML
    private void viewRequest() {
        Row sel = tblRequests.getSelectionModel().getSelectedItem();
        if (sel == null) { AppDialogs.warn("Lütfen bir talep seçin."); return; }
        try {
            FXMLLoader fxml = new FXMLLoader(getClass().getResource("view-request.fxml"));
            Parent view = fxml.load();

            ViewRequestController c = fxml.getController();
            c.setRequestId(sel.getId());

            Stage dlg = new Stage();
            dlg.setTitle("Talep Detayı – #" + sel.getId());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(tblRequests.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);
            dlg.showAndWait();
        } catch (IOException ex) {
            AppDialogs.unexpectedError("Talep detayı penceresi açma", ex);
        }
    }

    @FXML
    private void deleteSingleRequest() {
        Row sel = tblRequests.getSelectionModel().getSelectedItem();
        if (sel == null) { AppDialogs.warn("Silmek için bir talep seçin."); return; }

        Alert q = new Alert(Alert.AlertType.CONFIRMATION,
                "Talep #" + sel.getId() + " silinsin mi?", ButtonType.YES, ButtonType.NO);
        q.setHeaderText(null); q.setTitle("Onay");
        IconUtil.decorateAlert(q);
        q.showAndWait();

        if (q.getResult() != ButtonType.YES) return;

        tblRequests.setDisable(true);
        Async.runVoid(() -> {
                    try {
                        RequestDAO.deleteRequestById(sel.getId());
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, () -> {
                    AppDialogs.info("Talep silindi.");
                    refresh();
                }, ex -> AppDialogs.dbError("Talep silme", toSql(ex)),
                () -> tblRequests.setDisable(false));
    }

    /** JOIN’li özet sorgu kullanılıyor; arka planda yükle. */
    private void refresh() {
        tblRequests.setDisable(true);
        Async.run(() -> {
                    try {
                        var list = RequestDAO.findAllSummaries();
                        ObservableList<Row> tmp = FXCollections.observableArrayList();
                        for (RequestSummary s : list) {
                            tmp.add(new Row(s.getId(), s.getCustomerId(), s.getCustomerName(), s.getRequestDate(), s.getStatus()));
                        }
                        return tmp;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }, tmp -> rows.setAll(tmp),
                ex -> AppDialogs.dbError("Taleplerin yüklenmesi", toSql(ex)),
                () -> tblRequests.setDisable(false));
    }

    /** Liste satırı modeli. */
    public static class Row {
        private final int id;
        private final int customerId;
        private final String customerName;
        private final LocalDate requestDate;
        private final String status;
        public Row(int id, int customerId, String customerName, LocalDate requestDate, String status){
            this.id = id; this.customerId = customerId; this.customerName = customerName; this.requestDate = requestDate; this.status = status;
        }
        public int getId(){ return id; }
        public int getCustomerId(){ return customerId; }
        public String getCustomerName(){ return customerName; }
        public LocalDate getRequestDate(){ return requestDate; }
        public String getStatus(){ return status; }
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
}
