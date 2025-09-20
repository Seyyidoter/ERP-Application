package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;

/**
 * Müşteri modeli.
 * Para birimi alanları UI katmanında da BigDecimal olarak taşınır (kayan nokta hatalarını önlemek için).
 */
public class Customer {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty companyName;
    private final SimpleStringProperty contactPerson;
    private final SimpleStringProperty phone;
    private final SimpleStringProperty email;
    private final SimpleIntegerProperty iskonto;

    // Bakiye artık BigDecimal
    private final SimpleObjectProperty<BigDecimal> bakiye;

    // Bakiye dahil kurucu
    public Customer(int id, String companyName, String contactPerson, String phone, String email, int iskonto, BigDecimal bakiye) {
        this.id = new SimpleIntegerProperty(id);
        this.companyName = new SimpleStringProperty(companyName);
        this.contactPerson = new SimpleStringProperty(contactPerson);
        this.phone = new SimpleStringProperty(phone);
        this.email = new SimpleStringProperty(email);
        this.iskonto = new SimpleIntegerProperty(iskonto);
        this.bakiye = new SimpleObjectProperty<>(bakiye == null ? BigDecimal.ZERO : bakiye);
    }

    // Geri uyumluluk (bakiye yoksa 0)
    public Customer(int id, String companyName, String contactPerson, String phone, String email, int iskonto) {
        this(id, companyName, contactPerson, phone, email, iskonto, BigDecimal.ZERO);
    }

    // --- Getter'lar ---
    public int getId() { return id.get(); }
    public String getCompanyName() { return companyName.get(); }
    public String getContactPerson() { return contactPerson.get(); }
    public String getPhone() { return phone.get(); }
    public String getEmail() { return email.get(); }
    public int getIskonto() { return iskonto.get(); }

    /** Bakiye artık BigDecimal döner. */
    public BigDecimal getBalance() { return bakiye.get(); }

    // --- Property'ler ---
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty companyNameProperty() { return companyName; }
    public SimpleStringProperty contactPersonProperty() { return contactPerson; }
    public SimpleStringProperty phoneProperty() { return phone; }
    public SimpleStringProperty emailProperty() { return email; }
    public SimpleIntegerProperty iskontoProperty() { return iskonto; }

    public SimpleObjectProperty<BigDecimal> balanceProperty() { return bakiye; }

    // --- Setter'lar ---
    public void setCompanyName(String companyName) { this.companyName.set(companyName); }
    public void setContactPerson(String contactPerson) { this.contactPerson.set(contactPerson); }
    public void setPhone(String phone) { this.phone.set(phone); }
    public void setEmail(String email) { this.email.set(email); }
    public void setIskonto(int iskonto) { this.iskonto.set(iskonto); }

    public void setBalance(BigDecimal balance) { this.bakiye.set(balance == null ? BigDecimal.ZERO : balance); }
}
