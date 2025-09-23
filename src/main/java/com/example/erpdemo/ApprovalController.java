package com.example.erpdemo;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

/** Onay ekranı: bekleyen talepleri listeler, Görüntüle/Onayla/Reddet işlemlerini yapar. */
public class ApprovalController {

    @FXML private TableView<RequestRow>              pendingRequestsTable;
    @FXML private TableColumn<RequestRow, Integer>   idColumn;
    @FXML private TableColumn<RequestRow, Integer>   customerIdColumn;
    @FXML private TableColumn<RequestRow, String>    customerNameColumn;
    @FXML private TableColumn<RequestRow, LocalDate> dateColumn;
    @FXML private TableColumn<RequestRow, String>    statusColumn;

    @FXML private Button viewBtn;
    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;

    @FXML private HBox actionsBar;

    private final ObservableList<RequestRow> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // --- Sütun–model bağları
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        customerIdColumn.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        customerNameColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        try { DateUtil.setDateColumnDMY(dateColumn); } catch (Throwable ignore) {}

        pendingRequestsTable.setItems(rows);
        pendingRequestsTable.setPlaceholder(new Label("Bekleyen talep yok"));

        // --- GENİŞLİK KİLİTLEME / SÜTUN OYNATILMASIN (FXML’e koymuyoruz)
        pendingRequestsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        // pref genişliklerini sabitle (min=pref=max etkisi)
        idColumn.setPrefWidth(80);          idColumn.setMinWidth(80);          idColumn.setMaxWidth(80);
        customerIdColumn.setPrefWidth(100); customerIdColumn.setMinWidth(100); customerIdColumn.setMaxWidth(100);
        customerNameColumn.setPrefWidth(220); customerNameColumn.setMinWidth(220); customerNameColumn.setMaxWidth(220);
        dateColumn.setPrefWidth(150);       dateColumn.setMinWidth(150);       dateColumn.setMaxWidth(150);
        statusColumn.setPrefWidth(200);     statusColumn.setMinWidth(200);     statusColumn.setMaxWidth(200);

        pendingRequestsTable.getColumns().forEach(c -> {
            c.setReorderable(false);
            c.setResizable(false);
        });

        // Seçime bağlı butonlar
        var noSel = pendingRequestsTable.getSelectionModel().selectedItemProperty().isNull();
        viewBtn.disableProperty().bind(noSel);
        approveBtn.disableProperty().bind(noSel);
        rejectBtn.disableProperty().bind(noSel);

        // Tablo içi davranışlar
        pendingRequestsTable.setRowFactory(tv -> {
            TableRow<RequestRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    pendingRequestsTable.getSelectionModel().clearSelection();
                    if (pendingRequestsTable.getParent() != null)
                        pendingRequestsTable.getParent().requestFocus();
                } else if (e.getClickCount() == 2) {
                    handleView();
                }
            });
            return row;
        });

        // Tablo dışına tıklama
        javafx.application.Platform.runLater(() -> {
            if (pendingRequestsTable.getParent() != null)
                pendingRequestsTable.getParent().requestFocus();

            Scene scene = pendingRequestsTable.getScene();
            if (scene == null) return;

            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable   = isChildOf(n, pendingRequestsTable);
                boolean insideActions = isChildOf(n, actionsBar);
                if (!insideTable && !insideActions) {
                    pendingRequestsTable.getSelectionModel().clearSelection();
                    if (pendingRequestsTable.getParent() != null)
                        pendingRequestsTable.getParent().requestFocus();
                }
            });
        });

        refresh();
    }

    private static boolean isChildOf(Node n, Node root) {
        if (n == null || root == null) return false;
        while (n != null) {
            if (n == root) return true;
            n = n.getParent();
        }
        return false;
    }

    @FXML
    private void handleView() {
        RequestRow sel = pendingRequestsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        try {
            FXMLLoader fxml = new FXMLLoader(getClass().getResource("view-request.fxml"));
            Parent view = fxml.load();

            ViewRequestController c = fxml.getController();
            c.setOnChange(this::refresh);
            c.setRequestId(sel.getId());

            Stage dlg = new Stage();
            dlg.setTitle("Talep Detayı – #" + sel.getId());
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(pendingRequestsTable.getScene().getWindow());
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);
            dlg.showAndWait();
        } catch (IOException ex) {
            AppDialogs.unexpectedError("Talep detayı penceresi açma", ex);
        }
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
                },
                () -> {
                    AppDialogs.info("Talep onaylandı. Stok ve müşteri bakiyesi güncellendi.");
                    refresh();
                },
                ex -> AppDialogs.dbError("Talep onaylama", toSql(ex)),
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
                },
                () -> {
                    AppDialogs.info("Talep reddedildi.");
                    refresh();
                },
                ex -> AppDialogs.dbError("Talep reddetme", toSql(ex)),
                () -> setBusy(false));
    }

    /** Bekleyen talepleri yükle – müşteri adı zaten JOIN ile geliyor. */
    private void refresh() {
        setBusy(true);
        Async.run(() -> {
                    try {
                        var list = RequestDAO.getPendingSummaries();
                        ObservableList<RequestRow> tmp = FXCollections.observableArrayList();
                        for (RequestSummary s : list) {
                            tmp.add(new RequestRow(
                                    s.getId(), s.getCustomerId(), s.getCustomerName(),
                                    s.getRequestDate(), s.getStatus()
                            ));
                        }
                        return tmp;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                },
                tmp -> {
                    rows.setAll(tmp);
                    pendingRequestsTable.getSelectionModel().clearSelection();
                },
                ex -> AppDialogs.dbError("Bekleyen taleplerin yüklenmesi", toSql(ex)),
                () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        pendingRequestsTable.setDisable(busy);
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
        private final String customerName;
        private final LocalDate requestDate;
        private final String status;

        public RequestRow(int id, int customerId, String customerName, LocalDate requestDate, String status) {
            this.id = id;
            this.customerId = customerId;
            this.customerName = customerName;
            this.requestDate = requestDate;
            this.status = status;
        }
        public int getId() { return id; }
        public int getCustomerId() { return customerId; }
        public String getCustomerName() { return customerName; }
        public LocalDate getRequestDate() { return requestDate; }
        public String getStatus() { return status; }
    }

    /** (opsiyonel) dışarıdan çağırmak istersen */
    public void setCurrentUserId(int userId) { HelloApplication.setLoggedInUserId(userId); }
}
