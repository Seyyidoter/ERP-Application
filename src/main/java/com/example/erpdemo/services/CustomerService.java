package com.example.erpdemo.services;

import com.example.erpdemo.model.Customer;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CustomerService {
    List<Customer> listAll() throws Exception;

    void delete(int customerId) throws Exception;

    /** Pozitif tutar zorunlu. Not opsiyoneldir. */
    void addPayment(int customerId, BigDecimal amount, String note) throws Exception;

    /**
     * Rapor başlıkları için: id -> firma adı.
     * Herhangi bir Collection kabul eder; servis içerde Set'e dönüştürür.
     */
    Map<Integer, String> nameMapByIds(Collection<Integer> ids) throws Exception;
}
