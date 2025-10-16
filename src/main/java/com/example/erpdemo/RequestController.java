package com.example.erpdemo;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

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

    // Ekranın meşgul durumu (UI kilitleme için)
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    // Scene genelindeki dış tıklama filtresi (leak olmaması için referans tutuyoruz)
    private EventHandler<MouseEvent> outsideClickFilter;

    private volatile boolean disposed = false;

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

        // 3) Seçim/busy durumuna göre butonlar
        var selected = tblRequests.getSelectionModel().selectedItemProperty();
        var noSel    = selected.isNull();

        // Görüntüle: seçimsiz veya busy iken kapalı
        viewBtn.disableProperty().bind(noSel.or(busy));

        // Sil: yalnızca seçili ve DURUM=Onay Bekliyor ve busy değilken açık
        BooleanBinding notPending = Bindings.createBooleanBinding(
                () -> {
                    Row r = selected.get();
                    return r == null || !isPending(r.getStatus());
                },
                selected
        );
        deleteBtn.disableProperty().bind(busy.or(notPending));

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
        //    — filtreyi scene yaşam döngüsüne bağla (ekle/çıkar), leak/katlanma olmasın
        tblRequests.sceneProperty().addListener((obs, oldScene, newScene) -> {
            // Eski sahnede varsa filtremizi sökelim
            if (oldScene != null && outsideClickFilter != null) {
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
            }
            if (newScene != null) {
                // tek filter
                outsideClickFilter = e -> {
                    Node n = e.getPickResult().getIntersectedNode();
                    boolean insideTable   = isChildOf(n, tblRequests);
                    boolean insideActions = (actionsBar != null) && isChildOf(n, actionsBar);
                    if (!insideTable && !insideActions) {
                        tblRequests.getSelectionModel().clearSelection();
                        if (tblRequests.getParent() != null) tblRequests.getParent().requestFocus();
                    }
                };
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);

                // tek cleanup
                final EventHandler<WindowEvent> cleanup = we -> {
                    if (outsideClickFilter != null) {
                        newScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
                        outsideClickFilter = null;
                    }
                    disposed = true;
                };

                if (newScene.getWindow() != null) {
                    newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDING, cleanup);
                    newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, cleanup);
                } else {
                    newScene.windowProperty().addListener((o, ow, nw) -> {
                        if (nw != null) {
                            nw.addEventHandler(WindowEvent.WINDOW_HIDING, cleanup);
                            nw.addEventHandler(WindowEvent.WINDOW_HIDDEN, cleanup);
                        }
                    });
                }
            }
        });

        // 6) İlk yükleme
        refresh();
    }

    private static boolean isPending(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("onay bekliyor");
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

    private boolean uiDead() {
        if (disposed) return true;
        if (tblRequests == null) return true;
        var scene = tblRequests.getScene();
        if (scene == null) return true;
        var win = scene.getWindow();
        return (win == null || !win.isShowing());
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
                    if (uiDead()) return;
                    tblRequests.setDisable(false);
                    tblRequests.setMouseTransparent(false);
                    tblRequests.requestFocus();
                    owner.requestFocus();
                } catch (Throwable ignore) {}
            });

            dlg.showAndWait();

            // showAndWait dönüşünde de güvence
            if (!uiDead()) {
                tblRequests.setDisable(false);
                tblRequests.setMouseTransparent(false);
                tblRequests.requestFocus();
            }

        } catch (IOException ex) {
            AppDialogs.unexpectedError("Talep oluşturma penceresi açma", ex);
            try {
                if (!uiDead()) {
                    tblRequests.setDisable(false);
                    tblRequests.setMouseTransparent(false);
                    ((Stage) tblRequests.getScene().getWindow()).requestFocus();
                    tblRequests.requestFocus();
                }
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

        ButtonType EVET  = new ButtonType("Evet", ButtonBar.ButtonData.YES);
        ButtonType HAYIR = new ButtonType("Hayır", ButtonBar.ButtonData.NO);

        Alert q = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Talep #" + sel.getId() + " silinsin mi?",
                EVET, HAYIR
        );
        q.setHeaderText(null);
        q.setTitle("Onay");
        IconUtil.decorateAlert(q);
        q.initOwner(tblRequests.getScene().getWindow());
        q.showAndWait();

        if (q.getResult() != EVET) return;

        setControlsDisabled(true);
        Async.runVoid(() -> {
                    try { RequestDAO.deleteRequestById(sel.getId()); }
                    catch (SQLException ex) { throw new RuntimeException(ex); }
                },
                () -> {
                    if (uiDead()) return;
                    AppDialogs.info("Talep silindi.");
                    refresh();
                },
                ex -> {
                    if (uiDead()) return;
                    AppDialogs.dbError("Talep silme", toSql(ex));
                },
                () -> {
                    if (uiDead()) return;
                    setControlsDisabled(false);
                });
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
                tmp -> {
                    if (uiDead()) return;
                    rows.setAll(tmp);
                },
                ex  -> {
                    if (uiDead()) return;
                    AppDialogs.dbError("Taleplerin yüklenmesi", toSql(ex));
                },
                ()  -> {
                    if (uiDead()) return;
                    setControlsDisabled(false);
                });
    }

    private void setControlsDisabled(boolean disabled) {
        // Busy bayrağı: butonların disable binding’ine OR’lanıyor
        busy.set(disabled);

        if (tblRequests != null) {
            tblRequests.setDisable(disabled);
            tblRequests.setMouseTransparent(disabled); // bazı temalarda gerekli
        }
        if (actionsBar != null)  actionsBar.setDisable(disabled);
        // viewBtn / deleteBtn için setDisable çağırmıyoruz; binding var.
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
