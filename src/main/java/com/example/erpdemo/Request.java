package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import java.time.LocalDate;

public class Request {

    private final SimpleIntegerProperty id;
    private final SimpleIntegerProperty customerId;
    private final SimpleObjectProperty<LocalDate> requestDate;
    private final SimpleStringProperty status;

    public Request(int id, int customerId, LocalDate requestDate, String status) {
        this.id = new SimpleIntegerProperty(id);
        this.customerId = new SimpleIntegerProperty(customerId);
        this.requestDate = new SimpleObjectProperty<>(requestDate);
        this.status = new SimpleStringProperty(status);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public int getCustomerId() { return customerId.get(); }
    public LocalDate getRequestDate() { return requestDate.get(); }
    public String getStatus() { return status.get(); }

    // Property metotları
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleIntegerProperty customerIdProperty() { return customerId; }
    public SimpleObjectProperty<LocalDate> requestDateProperty() { return requestDate; }
    public SimpleStringProperty statusProperty() { return status; }
}