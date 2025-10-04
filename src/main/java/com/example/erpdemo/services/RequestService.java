package com.example.erpdemo.services;

import com.example.erpdemo.model.Request;
import com.example.erpdemo.model.RequestItem;
import com.example.erpdemo.model.RequestSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RequestService {

    // Listeleme
    List<RequestSummary> listSummaries(LocalDate from, LocalDate to) throws Exception;
    List<RequestSummary> getPendingSummaries() throws Exception;

    /** Raporlar için onaylanmış talepler (from/to null ise tüm zamanlar). */
    List<Request> listApproved(LocalDate from, LocalDate to) throws Exception;

    // Detay ekranı için
    RequestDetail getDetail(int requestId) throws Exception;
    List<RequestItem> getItems(int requestId) throws Exception;

    // Komutlar
    int createRequest(int customerId, List<RequestItem> items) throws Exception;
    void approve(int requestId, int approverId);   // domain/tx, hata -> DomainException
    void reject(int requestId, int approverId);    // domain/tx, hata -> DomainException
    void delete(int requestId) throws Exception;   // domain/tx (kalemlerle)

    // Toplam
    BigDecimal total(int requestId) throws Exception;

    /**
     * Raporlar için: Çok sayıda talebin kalemlerini tek seferde çeker ve talepId’ye göre gruplar.
     * SQL Server'ın parametre limitine (2100) takılmamak için içeride chunk’lanır.
     */
    Map<Integer, List<RequestItemView>> loadItemsGroupedByRequestIds(Set<Integer> requestIds) throws Exception;

    // Detay DTO (UI’nin header ihtiyacına birebir)
    record RequestDetail(int id, String customerName, LocalDate requestDate, String status) { }

    // Rapor satırı DTO’su (ürün adı, miktar, liste ve iskontolu fiyat)
    record RequestItemView(String productName, int quantity, BigDecimal listPrice, BigDecimal discountedPrice) { }
}
