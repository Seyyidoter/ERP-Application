package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Customer {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty companyName;
    private final SimpleStringProperty contactPerson;
    private final SimpleStringProperty phone;
    private final SimpleStringProperty email;
    private final SimpleIntegerProperty iskonto; // Bu kısım eksik

    public Customer(int id, String companyName, String contactPerson, String phone, String email, int iskonto) {
        this.id = new SimpleIntegerProperty(id);
        this.companyName = new SimpleStringProperty(companyName);
        this.contactPerson = new SimpleStringProperty(contactPerson);
        this.phone = new SimpleStringProperty(phone);
        this.email = new SimpleStringProperty(email);
        this.iskonto = new SimpleIntegerProperty(iskonto);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public String getCompanyName() { return companyName.get(); }
    public String getContactPerson() { return contactPerson.get(); }
    public String getPhone() { return phone.get(); }
    public String getEmail() { return email.get(); }
    public int getIskonto() { return iskonto.get(); }

    // Gerekli property metotları (JavaFX için)
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty companyNameProperty() { return companyName; }
    public SimpleStringProperty contactPersonProperty() { return contactPerson; }
    public SimpleStringProperty phoneProperty() { return phone; }
    public SimpleStringProperty emailProperty() { return email; }
    public SimpleIntegerProperty iskontoProperty() { return iskonto; }
}