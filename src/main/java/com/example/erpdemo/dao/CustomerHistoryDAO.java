package com.example.erpdemo.dao;

import com.example.erpdemo.model.CustomerHistoryRow;
import com.example.erpdemo.util.DatabaseManager;
import com.example.erpdemo.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/** Müşteri sipariş geçmişi – tarih filtrelerinde CAST kullanımı yok. */
public class CustomerHistoryDAO {

    /**
     * Belirli bir müşteri için geçmişi döndürür.
     *
     * @param customerId  Müşteri Id
     * @param from        Başlangıç tarihi (dahil); null ise sınırsız
     * @param to          Bitiş tarihi (dahil);   null ise sınırsız
     * @param statusLike  Durum tam eşleşme (örn. "Onaylandı"); null/boş ise hepsi
     * @param productLike Ürün adı LIKE filtresi; null/boş ise hepsi
     */
    public static ObservableList<CustomerHistoryRow> findHistoryForCustomer(
            int customerId,
            LocalDate from, LocalDate to,
            String statusLike,
            String productLike) throws SQLException {

        StringBuilder sb = new StringBuilder("""
            SELECT
                t.Id AS ReqId,
                CAST(t.TalepTarihi AS date) AS Tarih,   -- Görsellik için
                t.Durum,
                s.UrunAdi AS Urun,
                k.Miktar,
                k.TeklifFiyati
            FROM dbo.Talepler t
            JOIN dbo.TalepKalemleri k ON k.TalepId = t.Id
            JOIN dbo.Stoklar       s ON s.Id = k.UrunId
            WHERE t.MusteriId = ?
        """);

        // --- Tarih aralığı: indeks dostu ---
        if (from != null) sb.append(" AND t.TalepTarihi >= ? ");
        if (to   != null) sb.append(" AND t.TalepTarihi <  ? ");

        if (statusLike  != null && !statusLike.isBlank()) sb.append(" AND t.Durum = ? ");
        if (productLike != null && !productLike.isBlank()) sb.append(" AND s.UrunAdi LIKE ? ");

        sb.append(" ORDER BY t.Id DESC, k.Id ");

        ObservableList<CustomerHistoryRow> rows = FXCollections.observableArrayList();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {

            int i = 1;
            ps.setInt(i++, customerId);

            if (from != null) {
                ps.setTimestamp(i++, Timestamp.valueOf(from.atStartOfDay()));
            }
            if (to != null) {
                LocalDate next = to.plusDays(1);
                ps.setTimestamp(i++, Timestamp.valueOf(next.atStartOfDay()));
            }
            if (statusLike != null && !statusLike.isBlank()) {
                ps.setString(i++, statusLike);
            }
            if (productLike != null && !productLike.isBlank()) {
                ps.setString(i++, "%" + productLike + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reqId = rs.getInt("ReqId");
                    Date d = rs.getDate("Tarih");
                    LocalDate date = (d == null ? null : d.toLocalDate());
                    String status = rs.getString("Durum");
                    String product = rs.getString("Urun");
                    int qty = rs.getInt("Miktar");

                    BigDecimal unitRaw = rs.getBigDecimal("TeklifFiyati");
                    BigDecimal unit = Money.scale2(unitRaw);
                    BigDecimal subtotal = Money.scale2(unit.multiply(BigDecimal.valueOf(qty)));

                    rows.add(new CustomerHistoryRow(reqId, date, status, product, qty, unit, subtotal));
                }
            }
        }
        return rows;
    }
}
