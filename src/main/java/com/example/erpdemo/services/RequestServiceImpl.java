package com.example.erpdemo.services;

import com.example.erpdemo.dao.RequestDAO;
import com.example.erpdemo.exceptions.DomainException;
import com.example.erpdemo.model.Request;
import com.example.erpdemo.model.RequestItem;
import com.example.erpdemo.model.RequestSummary;
import com.example.erpdemo.util.DatabaseManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class RequestServiceImpl implements RequestService {

    private final DataSource ds = DatabaseManager.getDataSource();

    /* ==================== Query’ler ==================== */

    @Override
    public List<RequestSummary> listSummaries(LocalDate from, LocalDate to) throws Exception {
        if (from == null && to == null) return RequestDAO.findAllSummaries();
        return RequestDAO.findSummariesBetween(from, to);
    }

    @Override
    public List<RequestSummary> getPendingSummaries() throws Exception {
        return RequestDAO.getPendingSummaries();
    }

    @Override
    public List<Request> listApproved(LocalDate from, LocalDate to) throws Exception {
        if (from == null && to == null) {
            return RequestDAO.getApprovedRequests();
        }
        return RequestDAO.getApprovedRequestsBetween(from, to);
    }

    @Override
    public RequestDetail getDetail(int requestId) throws Exception {
        final String sql = """
            SELECT t.Id,
                   m.FirmaAdi     AS CustomerName,
                   t.TalepTarihi  AS RequestDate,
                   t.Durum        AS Status
            FROM dbo.Talepler t
            JOIN dbo.Musteriler m ON m.Id = t.MusteriId
            WHERE t.Id = ?
        """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new DomainException("Talep bulunamadı: #" + requestId);
                Date d = rs.getDate("RequestDate");
                LocalDate ld = (d == null ? null : d.toLocalDate());
                return new RequestDetail(
                        rs.getInt("Id"),
                        rs.getString("CustomerName"),
                        ld,
                        rs.getString("Status")
                );
            }
        }
    }

    @Override
    public List<RequestItem> getItems(int requestId) throws Exception {
        return new ArrayList<>(RequestDAO.getRequestItemsByRequestId(requestId));
    }

    /* ==================== Komutlar ==================== */

    @Override
    public int createRequest(int customerId, List<RequestItem> items) throws Exception {
        if (items == null || items.isEmpty())
            throw new DomainException("Talep için en az bir kalem gerekir.");
        if (items.stream().anyMatch(it -> it.getQuantity() <= 0))
            throw new DomainException("Miktar 0’dan büyük olmalıdır.");
        return RequestDAO.addRequestWithItems(customerId, items);
    }

    @Override
    public void approve(int requestId, int approverId) {
        try {
            RequestDAO.approveRequestTransactionally(requestId, approverId);
        } catch (SQLException e) {
            throw new DomainException("Onay başarısız: " + safeMsg(e), e);
        }
    }

    @Override
    public void reject(int requestId, int approverId) {
        try {
            RequestDAO.rejectRequest(requestId, approverId);
        } catch (SQLException e) {
            throw new DomainException("Reddetme başarısız: " + safeMsg(e), e);
        }
    }

    @Override
    public void delete(int requestId) throws Exception {
        int affected = RequestDAO.deleteRequestById(requestId);
        if (affected == 0) throw new DomainException("Talep bulunamadı veya silinemedi.");
    }

    @Override
    public BigDecimal total(int requestId) throws Exception {
        return RequestDAO.getRequestTotal(requestId);
    }

    /* ==================== Rapor yardımcıları ==================== */

    @Override
    public Map<Integer, List<RequestItemView>> loadItemsGroupedByRequestIds(Set<Integer> requestIds) throws Exception {
        if (requestIds == null || requestIds.isEmpty()) return Collections.emptyMap();

        // Çağıranın ekleme sırasını korumak için LinkedHashMap
        Map<Integer, List<RequestItemView>> map = new LinkedHashMap<>();

        // SQL Server parametre limiti (2100) – güvenli olsun diye 1000’lik parçalara bölelim
        final int CHUNK = 1000;
        List<Integer> ids = new ArrayList<>(requestIds);

        final String sqlPrefix = """
            SELECT tk.TalepId,
                   s.UrunAdi,
                   tk.Miktar,
                   s.Fiyat         AS ListeFiyati,
                   tk.TeklifFiyati AS IskontoluFiyat
            FROM dbo.TalepKalemleri tk
            JOIN dbo.Stoklar s ON s.Id = tk.UrunId
            WHERE tk.TalepId IN (
        """;
        final String sqlSuffix = ") ORDER BY tk.TalepId, tk.Id";

        try (Connection c = ds.getConnection()) {
            for (int start = 0; start < ids.size(); start += CHUNK) {
                List<Integer> chunk = ids.subList(start, Math.min(start + CHUNK, ids.size()));
                String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
                String sql = sqlPrefix + placeholders + sqlSuffix;

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    int i = 1;
                    for (Integer id : chunk) ps.setInt(i++, id);

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int rid = rs.getInt("TalepId");
                            RequestItemView row = new RequestItemView(
                                    rs.getString("UrunAdi"),
                                    rs.getInt("Miktar"),
                                    safeBD(rs.getBigDecimal("ListeFiyati")),
                                    safeBD(rs.getBigDecimal("IskontoluFiyat"))
                            );
                            map.computeIfAbsent(rid, k -> new ArrayList<>()).add(row);
                        }
                    }
                }
            }
        }
        return map;
    }

    private static BigDecimal safeBD(BigDecimal bd) {
        return (bd == null) ? BigDecimal.ZERO : bd;
    }

    private static String safeMsg(SQLException e) {
        String m = e.getMessage();
        return (m == null || m.isBlank())
                ? ("SQLSTATE=" + e.getSQLState() + " CODE=" + e.getErrorCode())
                : m;
    }
}
