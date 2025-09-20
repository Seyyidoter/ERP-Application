package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Talep detay penceresi. */
public class ViewRequestController {

    @FXML private Label requestIdLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label dateLabel;

    @FXML private TableView<ItemRow> requestItemsTable;
    @FXML private TableColumn<ItemRow, String>     productNameColumn;
    @FXML private TableColumn<ItemRow, Integer>    quantityColumn;
    @FXML private TableColumn<ItemRow, BigDecimal> discountedPriceColumn; // BigDecimal

    @FXML private Button closeBtn;

    private int requestId;

    @FXML
    public void initialize() {
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        discountedPriceColumn.setCellValueFactory(new PropertyValueFactory<>("discountedPrice"));

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        discountedPriceColumn.setCellFactory(MoneyCells.twoDecimalsTR());

        requestItemsTable.setPlaceholder(new Label("Kalem bulunmuyor."));
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
        loadData();
    }

    private void loadData() {
        try {
            Header h = fetchHeader(requestId);
            List<ItemRow> items = fetchItems(requestId);

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

    @FXML
    private void handleClose() {
        if (closeBtn != null && closeBtn.getScene() != null) {
            closeBtn.getScene().getWindow().hide();
        }
    }

    /** Başlık bilgisi */
    public record Header(int id, String customerName, LocalDate requestDate, String status) {}

    /** Tablo satırı modeli */
    public static class ItemRow {
        private final javafx.beans.property.SimpleStringProperty  productName     = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.SimpleIntegerProperty quantity        = new javafx.beans.property.SimpleIntegerProperty();
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

        public javafx.beans.property.SimpleStringProperty productNameProperty() { return productName; }
        public javafx.beans.property.SimpleIntegerProperty quantityProperty() { return quantity; }
        public javafx.beans.property.ObjectProperty<BigDecimal> discountedPriceProperty() { return discountedPrice; }
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        IconUtil.decorateAlert(a);
        a.showAndWait();
    }
}
