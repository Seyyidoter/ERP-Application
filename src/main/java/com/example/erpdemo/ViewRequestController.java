package com.example.erpdemo;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.stage.WindowEvent;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Talep detay penceresi (onaylama/reddetme destekli) */
public class ViewRequestController {

    @FXML private Label requestIdLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label dateLabel;

    @FXML private TableView<ItemRow> requestItemsTable;
    @FXML private TableColumn<ItemRow, String>     productNameColumn;
    @FXML private TableColumn<ItemRow, Integer>    quantityColumn;
    @FXML private TableColumn<ItemRow, BigDecimal> discountedPriceColumn;

    @FXML private Button approveBtn;
    @FXML private Button rejectBtn;
    @FXML private Button closeBtn;
    @FXML private javafx.scene.layout.HBox actionsBar;

    private int requestId;
    private Runnable onChange; // üst ekranı yenilemek için

    // Scene geneline eklediğimiz filtre referansı
    private EventHandler<MouseEvent> outsideClickFilter;

    private volatile boolean disposed = false;

    @FXML
    public void initialize() {
        // Sütun bağları
        productNameColumn.setCellValueFactory(c -> c.getValue().productNameProperty());
        quantityColumn.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        discountedPriceColumn.setCellValueFactory(c -> c.getValue().discountedPriceProperty());

        // Hücre biçimlendirme
        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        discountedPriceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        requestItemsTable.setPlaceholder(new Label("Kalem bulunmuyor."));

        // Tablo içinde boş alana tıklanınca seçimi/odağı temizle
        requestItemsTable.setRowFactory(tv -> {
            TableRow<ItemRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (row.isEmpty()) {
                    requestItemsTable.getSelectionModel().clearSelection();
                    if (requestItemsTable.getParent() != null) requestItemsTable.getParent().requestFocus();
                }
            });
            return row;
        });

        // Pencere açıldığında tablo fokus almasın
        if (requestItemsTable.getParent() != null) {
            requestItemsTable.getParent().requestFocus();
        }

        // Dış tıklama filtresini scene yaşam döngüsüne bağla + kapanınca disposed
        requestItemsTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && outsideClickFilter != null) {
                oldScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
            }
            if (newScene != null) {
                outsideClickFilter = e -> {
                    Node n = e.getPickResult().getIntersectedNode();
                    boolean insideTable   = isChildOf(n, requestItemsTable);
                    boolean insideActions = actionsBar != null && isChildOf(n, actionsBar);
                    if (!insideTable && !insideActions) {
                        requestItemsTable.getSelectionModel().clearSelection();
                        if (requestItemsTable.getParent() != null) requestItemsTable.getParent().requestFocus();
                    }
                };
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);

                if (newScene.getWindow() != null) {
                    newScene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, we -> {
                        disposed = true;
                        if (outsideClickFilter != null) {
                            newScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
                            outsideClickFilter = null;
                        }
                    });
                } else {
                    newScene.windowProperty().addListener((o, ow, nw) -> {
                        if (nw != null) {
                            nw.addEventHandler(WindowEvent.WINDOW_HIDDEN, we -> {
                                disposed = true;
                                if (outsideClickFilter != null) {
                                    newScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
                                    outsideClickFilter = null;
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    /** n düğümü root’un altındaysa true */
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
        if (requestItemsTable == null) return true;
        var scene = requestItemsTable.getScene();
        if (scene == null) return true;
        var win = scene.getWindow();
        return (win == null || !win.isShowing());
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
        loadData();
    }

    /** Üst taraftan (RequestController/ApprovalController) yenileme için callback atanır. */
    public void setOnChange(Runnable r) { this.onChange = r; }

    private void loadData() {
        setBusy(true);
        Async.run(() -> {
                    try {
                        Header h = fetchHeader(requestId);
                        List<ItemRow> items = fetchItems(requestId);
                        return new Object[]{h, items};
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                payload -> {
                    if (uiDead()) return;
                    Header h = (Header) payload[0];
                    @SuppressWarnings("unchecked")
                    List<ItemRow> items = (List<ItemRow>) payload[1];

                    requestIdLabel.setText(String.valueOf(requestId));
                    customerNameLabel.setText(h.customerName());
                    statusLabel.setText(h.status());

                    LocalDate d = h.requestDate();
                    dateLabel.setText(d == null ? "—" : DateUtil.fmt(d));

                    requestItemsTable.getItems().setAll(items);

                    // yalnızca 'Onay Bekliyor' ise butonları göster/etkinleştir
                    updateActionButtons(h.status());
                },
                ex -> {
                    if (uiDead()) return;
                    AppDialogs.dbError("Talep detayı yükleme", toSql(ex));
                    statusLabel.setText("Hata");
                    updateActionButtons("Hata");
                },
                () -> {
                    if (uiDead()) return;
                    setBusy(false);
                });
    }

    /** DAO’da hazır olmadığı için başlığı buradan çekiyoruz. */
    private Header fetchHeader(int id) throws SQLException {
        String sql = """
            SELECT t.Id,
                   m.FirmaAdi     AS CustomerName,
                   t.TalepTarihi  AS RequestDate,
                   t.Durum        AS Status
            FROM dbo.Talepler t
            JOIN dbo.Musteriler m ON m.Id = t.MusteriId
            WHERE t.Id = ?
        """;
        try (var c = DatabaseManager.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Talep bulunamadı: #" + id);
                var req = rs.getDate("RequestDate");
                LocalDate d = (req != null ? req.toLocalDate() : null);
                return new Header(id, rs.getString("CustomerName"), d, rs.getString("Status"));
            }
        }
    }

    private List<ItemRow> fetchItems(int id) throws SQLException {
        var daoItems = RequestDAO.getRequestItemsByRequestId(id);
        List<ItemRow> list = new ArrayList<>();
        for (RequestItem it : daoItems) {
            list.add(new ItemRow(it.getProductName(), it.getQuantity(), it.getDiscountedPrice()));
        }
        return list;
    }

    @FXML private void handleApprove() { approveReject(true); }
    @FXML private void handleReject()  { approveReject(false); }

    private void approveReject(boolean approve) {
        int uid = HelloApplication.getLoggedInUserId();
        if (uid <= 0) {
            AppDialogs.warn("Oturum bilgisi eksik. Lütfen yeniden giriş yapın.");
            return;
        }

        setBusy(true);

        Async.runVoid(() -> {
            try {
                // --- 0) Son durumu tekrar kontrol et (yarışlara karşı) ---
                Header h = fetchHeader(requestId);
                if (!isPending(h.status())) {
                    throw new IllegalStateException(
                            "Talep artık '" + h.status() + "' durumunda. İşlem iptal edildi.");
                }

                // --- 1) İşlem ---
                if (approve) {
                    RequestDAO.approveRequestTransactionally(requestId, uid);
                } else {
                    RequestDAO.rejectRequest(requestId, uid);
                }
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, () -> {
            if (uiDead()) return;
            AppDialogs.info(approve ? "Talep onaylandı. Stok ve müşteri bakiyesi güncellendi." : "Talep reddedildi.");
            if (onChange != null) onChange.run(); // üst listeyi yenile
            handleClose();
        }, ex -> {
            if (uiDead()) return;
            // Yarış/Geçersiz durum uyarısını kibar göster
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalStateException ise) {
                AppDialogs.warn(ise.getMessage() + "\nEkran güncellenecek.");
                loadData(); // status ve butonlar güncellensin
            } else {
                AppDialogs.dbError(approve ? "Talep onaylama" : "Talep reddetme", toSql(ex));
            }
        }, () -> {
            if (uiDead()) return;
            setBusy(false);
        });
    }

    private void updateActionButtons(String status) {
        boolean canDecide = isPending(status); // SADECE 'Onay Bekliyor'
        approveBtn.setVisible(canDecide);  approveBtn.setManaged(canDecide);
        rejectBtn.setVisible(canDecide);   rejectBtn.setManaged(canDecide);

        // görünür değilse zaten devre dışı olur; yine de güvenlik için
        approveBtn.setDisable(!canDecide);
        rejectBtn.setDisable(!canDecide);
    }

    private static boolean isPending(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("onay bekliyor");
    }

    private void setBusy(boolean busy) {
        // NOT: approve/reject butonlarına dokunmuyoruz; durumlarını updateActionButtons belirler.
        if (requestItemsTable != null) requestItemsTable.setDisable(busy);
        if (actionsBar != null)        actionsBar.setDisable(busy);
        if (closeBtn != null)          closeBtn.setDisable(busy);
    }

    @FXML
    private void handleClose() {
        if (requestItemsTable != null && requestItemsTable.getScene() != null) {
            requestItemsTable.getScene().getWindow().hide();
        }
    }

    /** Başlık bilgisi */
    public record Header(int id, String customerName, LocalDate requestDate, String status) {}

    /** Tablo satırı modeli */
    public static class ItemRow {
        private final javafx.beans.property.SimpleStringProperty  productName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.SimpleIntegerProperty quantity    = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.ObjectProperty<BigDecimal> discountedPrice =
                new javafx.beans.property.SimpleObjectProperty<>(BigDecimal.ZERO);

        public ItemRow(String productName, int quantity, BigDecimal discountedPrice) {
            this.productName.set(productName);
            this.quantity.set(quantity);
            this.discountedPrice.set(discountedPrice == null ? BigDecimal.ZERO : discountedPrice);
        }

        public String getProductName() { return productName.get(); }
        public int getQuantity() { return quantity.get(); }
        public BigDecimal getDiscountedPrice() { return discountedPrice.get(); }

        public javafx.beans.property.StringProperty productNameProperty() { return productName; }
        public javafx.beans.property.IntegerProperty quantityProperty() { return quantity; }
        public javafx.beans.property.ObjectProperty<BigDecimal> discountedPriceProperty() { return discountedPrice; }
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
