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

/** Talep listesi ekranı (Görüntüle/Sil + Tarih filtresi + asenkron yükleme) */
public class RequestController {

    @FXML private TableView<Row> tblRequests;
    @FXML private TableColumn<Row, Integer>   colId;
    @FXML private TableColumn<Row, Integer>   colCustomer;
    @FXML private TableColumn<Row, String>    colCustomerName;
    @FXML private TableColumn<Row, LocalDate> colDate;
    @FXML private TableColumn<Row, String>    colStatus;

    // 🔸 Tarih filtreleri
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;

    // Seçime bağlı butonlar
    @FXML private Button viewBtn;
    @FXML private Button deleteBtn;

    // Alt buton çubuğu (FXML’de fx:id="actionsBar")
    @FXML private HBox actionsBar;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 1) Sütun–model bağları
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        colCustomerName.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        try { DateUtil.setDateColumnDMY(colDate); } catch (Throwable ignore) {}

        // 2) Tablo: placeholder + veri listesi
        tblRequests.setPlaceholder(new Label("Kayıtlı talep yok"));
        tblRequests.setItems(rows);

        // 3) Seçim yokken butonları pasifleştir
        var noSel = tblRequests.getSelectionModel().selectedItemProperty().isNull();
        viewBtn.disableProperty().bind(noSel);
        deleteBtn.disableProperty().bind(noSel);

        // 4) Tabloda boş alana tıklayınca seçimi/odağı temizle, çift tık → görüntüle
        tblRequests.setRowFactory(tv -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    tblRequests.getSelectionModel().clearSelection();
                    if (tblRequests.getParent() != null) tblRequests.getParent().requestFocus();
                } else if (e.getClickCount() == 2) {
                    tblRequests.getSelectionModel().select(row.getIndex());
                    viewRequest();
                }
            });
            return row;
        });

        // 5) SAHNE GENELİ: tablo ve actionsBar dışına tıklanırsa seçimi temizle
        javafx.application.Platform.runLater(() -> {
            Scene scene = tblRequests.getScene();
            if (scene == null) return;
            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable   = isChildOf(n, tblRequests);
                boolean insideActions = isChildOf(n, actionsBar);
                if (!insideTable && !insideActions) {
                    tblRequests.getSelectionModel().clearSelection();
                    if (tblRequests.getParent() != null) tblRequests.getParent().requestFocus();
                }
            });
        });

        // 6) İlk yükleme
        refresh();
    }

    /** n düğümü root’un altındaysa true. */
    private static boolean isChildOf(Node n, Node root) {
        if (n == null || root == null) return false;
        while (n != null) {
            if (n == root) return true;
            n = n.getParent();
        }
        return false;
    }

    // 🔸 Filtre butonları
    @FXML private void handleApplyFilters() { refresh(); }
    @FXML private void handleClearFilters() {
        if (dpFrom != null) dpFrom.setValue(null);
        if (dpTo   != null) dpTo.setValue(null);
        refresh();
    }

    @FXML
    private void createRequest() {
        try {
            FXMLLoader fxml = new FXMLLoader(getClass().getResource("new-request.fxml"));
            Parent view = fxml.load();

            // Controller’ı al – sadece “Kaydet” olunca listeyi yenile
            NewRequestController c = fxml.getController();
            c.setOnSaved(this::refresh);

            Stage owner = (Stage) tblRequests.getScene().getWindow();

            Stage dlg = new Stage();
            dlg.setTitle("Yeni Talep");
            dlg.initModality(Modality.WINDOW_MODAL);
            dlg.initOwner(owner);
            dlg.setScene(new Scene(view));
            IconUtil.setAppIcon(dlg);

            // Kapanışta odağı ve etkileşimi garanti altına al
            dlg.setOnHidden(e -> {
                try {
                    tblRequests.setDisable(false);
                    tblRequests.setMouseTransparent(false);
                    tblRequests.requestFocus();
                    owner.requestFocus();
                } catch (Throwable ignore) {}
            });

            dlg.showAndWait();

            // showAndWait dönüşünde de güvence
            tblRequests.setDisable(false);
            tblRequests.setMouseTransparent(false);
            tblRequests.requestFocus();

        } catch (IOException ex) {
            AppDialogs.unexpectedError("Talep oluşturma penceresi açma", ex);
            try {
                tblRequests.setDisable(false);
                tblRequests.setMouseTransparent(false);
                ((Stage) tblRequests.getScene().getWindow()).requestFocus();
                tblRequests.requestFocus();
            } catch (Throwable ignore) {}
        }
    }

    @FXML
    private void viewRequest() {
        Row sel = tblRequests.getSelectionModel().getSelectedItem();
        if (sel == null) return; // buton zaten disabled
        try {
            FXMLLoader fxml = new FXMLLoader(getClass().getResource("view-request.fxml"));
            Parent view = fxml.load();

            ViewRequestController c = fxml.getController();
            c.setRequestId(sel.getId());
            c.setOnChange(this::refresh);

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
        if (sel == null) return;

        Alert q = new Alert(Alert.AlertType.CONFIRMATION,
                "Talep #" + sel.getId() + " silinsin mi?", ButtonType.YES, ButtonType.NO);
        q.setHeaderText(null); q.setTitle("Onay");
        IconUtil.decorateAlert(q);
        q.showAndWait();

        if (q.getResult() != ButtonType.YES) return;

        setControlsDisabled(true);
        Async.runVoid(() -> {
                    try { RequestDAO.deleteRequestById(sel.getId()); }
                    catch (SQLException ex) { throw new RuntimeException(ex); }
                },
                () -> { AppDialogs.info("Talep silindi."); refresh(); },
                ex -> AppDialogs.dbError("Talep silme", toSql(ex)),
                () -> setControlsDisabled(false));
    }

    /** JOIN’li özetleri tarih filtresiyle yükler; arka planda. */
    private void refresh() {
        final LocalDate from = (dpFrom == null) ? null : dpFrom.getValue();
        final LocalDate to   = (dpTo   == null) ? null : dpTo.getValue();

        setControlsDisabled(true);
        Async.run(() -> {
                    try {
                        var list = (from == null && to == null)
                                ? RequestDAO.findAllSummaries()
                                : RequestDAO.findSummariesBetween(from, to);

                        var tmp = FXCollections.<Row>observableArrayList();
                        for (RequestSummary s : list) {
                            tmp.add(new Row(s.getId(), s.getCustomerId(), s.getCustomerName(),
                                    s.getRequestDate(), s.getStatus()));
                        }
                        return tmp;
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                },
                tmp -> rows.setAll(tmp),
                ex  -> AppDialogs.dbError("Taleplerin yüklenmesi", toSql(ex)),
                ()  -> setControlsDisabled(false));
    }

    private void setControlsDisabled(boolean disabled) {
        if (tblRequests != null) {
            tblRequests.setDisable(disabled);
            tblRequests.setMouseTransparent(disabled); // bazı temalarda gerekli
        }
        if (actionsBar != null)  actionsBar.setDisable(disabled);
        if (viewBtn != null && viewBtn.isDisable() != disabled) viewBtn.setDisable(disabled);
        if (deleteBtn != null && deleteBtn.isDisable() != disabled) deleteBtn.setDisable(disabled);
    }

    /** Liste satırı modeli. */
    public static class Row {
        private final int id;
        private final int customerId;
        private final String customerName;
        private final LocalDate requestDate;
        private final String status;
        public Row(int id, int customerId, String customerName, LocalDate requestDate, String status){
            this.id = id; this.customerId = customerId; this.customerName = customerName;
            this.requestDate = requestDate; this.status = status;
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
