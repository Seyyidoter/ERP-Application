package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.math.BigDecimal;

/** Müşteri modeli (UI-dostu property'ler + BigDecimal bakiye). */
public class Customer {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty  companyName;
    private final SimpleStringProperty  contactPerson;
    private final SimpleStringProperty  phone;
    private final SimpleStringProperty  email;
    private final SimpleIntegerProperty iskonto; // yüzde (0..100)
    private final SimpleObjectProperty<BigDecimal> balance;

    public Customer(int id, String companyName, String contactPerson,
                    String phone, String email, int iskonto, BigDecimal balance) {
        this.id = new SimpleIntegerProperty(id);
        this.companyName   = new SimpleStringProperty(companyName);
        this.contactPerson = new SimpleStringProperty(contactPerson);
        this.phone         = new SimpleStringProperty(phone);
        this.email         = new SimpleStringProperty(email);
        this.iskonto       = new SimpleIntegerProperty(iskonto);
        this.balance       = new SimpleObjectProperty<>(balance == null ? BigDecimal.ZERO : balance);
    }

    public Customer(int id, String companyName, String contactPerson,
                    String phone, String email, int iskonto) {
        this(id, companyName, contactPerson, phone, email, iskonto, BigDecimal.ZERO);
    }

    // --- getters ---
    public int getId() { return id.get(); }
    public String getCompanyName() { return companyName.get(); }
    public String getContactPerson() { return contactPerson.get(); }
    public String getPhone() { return phone.get(); }
    public String getEmail() { return email.get(); }
    public int getIskonto() { return iskonto.get(); }
    public BigDecimal getBalance() { return balance.get(); }

    // --- properties (TableView binding) ---
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty companyNameProperty() { return companyName; }
    public SimpleStringProperty contactPersonProperty() { return contactPerson; }
    public SimpleStringProperty phoneProperty() { return phone; }
    public SimpleStringProperty emailProperty() { return email; }
    public SimpleIntegerProperty iskontoProperty() { return iskonto; }
    public SimpleObjectProperty<BigDecimal> balanceProperty() { return balance; }

    // --- setters ---
    public void setCompanyName(String v) { companyName.set(v); }
    public void setContactPerson(String v) { contactPerson.set(v); }
    public void setPhone(String v) { phone.set(v); }
    public void setEmail(String v) { email.set(v); }
    public void setIskonto(int v) { iskonto.set(v); }
    public void setBalance(BigDecimal v) { balance.set(v == null ? BigDecimal.ZERO : v); }
}
