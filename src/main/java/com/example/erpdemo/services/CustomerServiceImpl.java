package com.example.erpdemo.services;

import com.example.erpdemo.dao.CustomerDAO;
import com.example.erpdemo.dao.PaymentDAO;
import com.example.erpdemo.model.Customer;

import java.math.BigDecimal;
import java.util.*;

public class CustomerServiceImpl implements CustomerService {

    @Override
    public List<Customer> listAll() throws Exception {
        return CustomerDAO.getAllCustomers();
    }

    @Override
    public void delete(int customerId) throws Exception {
        CustomerDAO.deleteCustomer(customerId);
    }

    @Override
    public void addPayment(int customerId, BigDecimal amount, String note) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Ödeme tutarı 0’dan büyük olmalıdır.");
        }
        String desc = (note == null) ? "" : note.trim();
        PaymentDAO.addPayment(customerId, amount, desc);
    }

    @Override
    public Map<Integer, String> nameMapByIds(Collection<Integer> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        // DAO Set<Integer> bekliyor — koleksiyonu kayıpsız şekilde Set'e dönüştür.
        // LinkedHashSet kullanarak orijinal sıralamayı (varsa) koruyoruz.
        Set<Integer> idSet = (ids instanceof Set<?> s)
                ? (Set<Integer>) s
                : new LinkedHashSet<>(ids);

        return CustomerDAO.getCustomerNamesByIds(idSet);
    }
}
