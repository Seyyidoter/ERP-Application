package com.example.erpdemo;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.sql.*;
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
    @FXML private TableColumn<ItemRow, String>  productNameColumn;
    @FXML private TableColumn<ItemRow, Integer> quantityColumn;
    @FXML private TableColumn<ItemRow, Double>  discountedPriceColumn;

    @FXML private Button closeBtn;

    private int requestId;

    @FXML
    public void initialize() {
        productNameColumn.setCellValueFactory(c -> c.getValue().productNameProperty());
        quantityColumn.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        discountedPriceColumn.setCellValueFactory(c -> c.getValue().discountedPriceProperty().asObject());

        quantityColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        discountedPriceColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
        loadData();
    }

    private void loadData() {
        try {
            Header h = fetchHeader(requestId);              // başlık
            List<ItemRow> items = fetchItems(requestId);    // kalemler

            requestIdLabel.setText(String.valueOf(requestId));
            customerNameLabel.setText(h.customerName());
            statusLabel.setText(h.status());
            dateLabel.setText(DateUtil.fmt(h.requestDate()));

            requestItemsTable.getItems().setAll(items);
        } catch (SQLException ex) {
            statusLabel.setText("Hata: " + ex.getMessage());
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
                LocalDate d = rs.getDate("RequestDate").toLocalDate();
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
    private void handleClose() { closeBtn.getScene().getWindow().hide(); }

    /** Başlık bilgisi */
    public record Header(int id, String customerName, LocalDate requestDate, String status) {}

    /** Basit item satırı */
    public static class ItemRow extends SimpleRowBase {
        public ItemRow(String p, int q, double dp) { super(p,q,dp); }
    }

    public static class SimpleRowBase extends javafx.beans.binding.StringExpression {
        private final javafx.beans.property.SimpleStringProperty productName = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.SimpleIntegerProperty quantity = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.SimpleDoubleProperty discountedPrice = new javafx.beans.property.SimpleDoubleProperty();

        public SimpleRowBase() {}
        public SimpleRowBase(String p, int q, double dp) {
            productName.set(p); quantity.set(q); discountedPrice.set(dp);
        }

        public javafx.beans.property.SimpleStringProperty productNameProperty(){ return productName; }
        public javafx.beans.property.SimpleIntegerProperty quantityProperty(){ return quantity; }
        public javafx.beans.property.SimpleDoubleProperty discountedPriceProperty(){ return discountedPrice; }

        @Override public String get() { return productName.get(); }
        @Override public void addListener(javafx.beans.value.ChangeListener<? super String> listener) {}
        @Override public void removeListener(javafx.beans.value.ChangeListener<? super String> listener) {}
        @Override public void addListener(javafx.beans.InvalidationListener listener) {}
        @Override public void removeListener(javafx.beans.InvalidationListener listener) {}
        @Override public String getValue() { return get(); }
    }
}
