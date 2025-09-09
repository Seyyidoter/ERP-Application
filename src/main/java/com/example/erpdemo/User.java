package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class User {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty username; // KullaniciAdi -> username
    private final SimpleStringProperty role; // rol -> role

    public User(int id, String username, String role) {
        this.id = new SimpleIntegerProperty(id);
        this.username = new SimpleStringProperty(username);
        this.role = new SimpleStringProperty(role);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public String getUsername() { return username.get(); }
    public String getRole() { return role.get(); }
}