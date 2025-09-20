package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Talep detay penceresi. */
public class ViewRequestController {

    @FXML private Label requestIdLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label dateLabel;

    @FXML private TableView<ItemRow> requestItemsTable;
    @FXML private TableColumn<ItemRow, String>  productNameColumn;
    @FXML private TableColumn<ItemRow, Integer> quantityColumn;
    @FXML private TableColumn<ItemRow, Double>  discountedPriceColumn;

    @FXML private Button closeBtn;

    private int requestId;

    @FXML
    public void initialize() {
        // Kolon bağları
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        // Sayısal hizalama + TR para biçimi
        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        discountedPriceColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format(Locale.forLanguageTag("tr-TR"), "%.2f", v));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                }
            }
        });

        requestItemsTable.setPlaceholder(new Label("Kalem bulunmuyor."));
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
        loadData();
    }

    private void loadData() {
        try {
            Header h = fetchHeader(requestId);           // başlık
            List<ItemRow> items = fetchItems(requestId); // kalemler

            requestIdLabel.setText(String.valueOf(requestId));
            customerNameLabel.setText(h.customerName());
            statusLabel.setText(h.status());

            LocalDate d = h.requestDate();
            dateLabel.setText(d == null ? "—" : DateUtil.fmt(d));

            requestItemsTable.getItems().setAll(items);

        } catch (SQLException ex) {
            showError("Hata", "Talep detayı yüklenemedi:\n" + ex.getMessage());
            statusLabel.setText("Hata");
        }
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
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Talep bulunamadı: #" + id);
                Date req = rs.getDate("RequestDate"); // null güvenli
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

    @FXML
    private void handleClose() {
        if (closeBtn != null && closeBtn.getScene() != null) {
            closeBtn.getScene().getWindow().hide();
        }
    }

    /** Başlık bilgisi */
    public record Header(int id, String customerName, LocalDate requestDate, String status) {}

    /** Tablo satırı modeli (JavaFX property’leriyle) */
    public static class ItemRow {
        private final javafx.beans.property.SimpleStringProperty productName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.SimpleIntegerProperty quantity    = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.SimpleDoubleProperty  discountedPrice = new javafx.beans.property.SimpleDoubleProperty();

        public ItemRow(String productName, int quantity, double discountedPrice) {
            this.productName.set(productName);
            this.quantity.set(quantity);
            this.discountedPrice.set(discountedPrice);
        }

        // Getter’lar
        public String getProductName() { return productName.get(); }
        public int getQuantity() { return quantity.get(); }
        public double getDiscountedPrice() { return discountedPrice.get(); }

        // Property’ler (PropertyValueFactory için)
        public javafx.beans.property.SimpleStringProperty productNameProperty() { return productName; }
        public javafx.beans.property.SimpleIntegerProperty quantityProperty() { return quantity; }
        public javafx.beans.property.SimpleDoubleProperty discountedPriceProperty() { return discountedPrice; }
    }

    // --- küçük yardımcı ---
    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
