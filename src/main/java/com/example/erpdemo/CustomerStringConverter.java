// CustomerStringConverter.java
package com.example.erpdemo;
import javafx.util.StringConverter;
public class CustomerStringConverter extends StringConverter<Customer> {
    @Override
    public String toString(Customer customer) {
        return customer != null ? customer.getCompanyName() : "";
    }
    @Override
    public Customer fromString(String s) {
        return null; // Tersine dönüştürme şimdilik gerekli değil
    }
}
