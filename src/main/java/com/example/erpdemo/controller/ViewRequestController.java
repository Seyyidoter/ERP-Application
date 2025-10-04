package com.example.erpdemo.controller;

import com.example.erpdemo.app.HelloApplication;
import com.example.erpdemo.model.RequestItem;
import com.example.erpdemo.services.RequestService;
import com.example.erpdemo.services.RequestServiceImpl;
import com.example.erpdemo.util.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

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

    // --- Yalnız servis katmanı
    private final RequestService requestService = new RequestServiceImpl();

    @FXML
    public void initialize() {
        // Sütun bağları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

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

        // Pencere açıldığında tablo fokus almasın, dış tıklamalarda seçimi temizle
        javafx.application.Platform.runLater(() -> {
            if (requestItemsTable.getParent() != null) {
                requestItemsTable.getParent().requestFocus();
            }
            var scene = requestItemsTable.getScene();
            if (scene == null) return;

            scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                Node n = e.getPickResult().getIntersectedNode();
                boolean insideTable   = isChildOf(n, requestItemsTable);
                boolean insideActions = actionsBar != null && isChildOf(n, actionsBar);
                if (!insideTable && !insideActions) {
                    requestItemsTable.getSelectionModel().clearSelection();
                    if (requestItemsTable.getParent() != null) requestItemsTable.getParent().requestFocus();
                }
            });
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
                        var header = requestService.getDetail(requestId); // id, customerName, date, status
                        var items  = requestService.getItems(requestId);
                        return new Object[]{header, items};
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                payload -> {
                    RequestService.RequestDetail h = (RequestService.RequestDetail) payload[0];
                    @SuppressWarnings("unchecked")
                    List<RequestItem> svcItems = (List<RequestItem>) payload[1];

                    requestIdLabel.setText(String.valueOf(requestId));
                    customerNameLabel.setText(h.customerName());
                    statusLabel.setText(h.status());

                    LocalDate d = h.requestDate();
                    dateLabel.setText(d == null ? "—" : DateUtil.fmt(d));

                    // Service -> UI satırı dönüşümü
                    List<ItemRow> rows = new ArrayList<>();
                    for (RequestItem it : svcItems) {
                        rows.add(new ItemRow(it.getProductName(), it.getQuantity(), it.getDiscountedPrice()));
                    }
                    requestItemsTable.getItems().setAll(rows);

                    // yalnızca 'Onay Bekliyor' ise butonları göster/etkinleştir
                    updateActionButtons(h.status());
                },
                ex -> {
                    AppDialogs.dbError("Talep detayı yükleme", toSql(ex));
                    statusLabel.setText("Hata");
                    updateActionButtons("Hata");
                },
                () -> setBusy(false));
    }

    @FXML private void handleApprove() { approveReject(true); }
    @FXML private void handleReject()  { approveReject(false); }

    private void approveReject(boolean approve) {
        setBusy(true);

        Async.runVoid(() -> {
            try {
                // --- 0) Son durumu tekrar kontrol et (yarışlara karşı) ---
                var h = requestService.getDetail(requestId);
                if (!isPending(h.status())) {
                    throw new IllegalStateException(
                            "Talep artık '" + h.status() + "' durumunda. İşlem iptal edildi.");
                }

                // --- 1) İşlem ---
                if (approve) {
                    requestService.approve(requestId, HelloApplication.getLoggedInUserId());
                } else {
                    requestService.reject(requestId, HelloApplication.getLoggedInUserId());
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, () -> {
            AppDialogs.info(approve ? "Talep onaylandı." : "Talep reddedildi.");
            if (onChange != null) onChange.run(); // üst listeyi yenile
            handleClose();
        }, ex -> {
            // Yarış/Geçersiz durum uyarısını kibar göster
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalStateException ise) {
                AppDialogs.warn(ise.getMessage() + "\nEkran güncellenecek.");
                loadData(); // status ve butonlar güncellensin
            } else {
                AppDialogs.dbError(approve ? "Talep onaylama" : "Talep reddetme", toSql(ex));
            }
        }, () -> setBusy(false));
    }

    private void updateActionButtons(String status) {
        boolean canDecide = isPending(status); // SADECE 'Onay Bekliyor'
        approveBtn.setVisible(canDecide);  approveBtn.setManaged(canDecide);
        rejectBtn.setVisible(canDecide);   rejectBtn.setManaged(canDecide);

        approveBtn.setDisable(!canDecide);
        rejectBtn.setDisable(!canDecide);
    }

    private static boolean isPending(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("onay bekliyor");
    }

    private void setBusy(boolean busy) {
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

    /** Tablo satırı modeli (UI DTO) */
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
