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
    private final SimpleIntegerProperty approverId;
    private final SimpleObjectProperty<LocalDate> approvalDate;

    public Request(int id, int customerId, LocalDate requestDate, String status, Integer approverId, LocalDate approvalDate) {
        this.id = new SimpleIntegerProperty(id);
        this.customerId = new SimpleIntegerProperty(customerId);
        this.requestDate = new SimpleObjectProperty<>(requestDate);
        this.status = new SimpleStringProperty(status);
        this.approverId = new SimpleIntegerProperty(approverId != null ? approverId : 0);
        this.approvalDate = new SimpleObjectProperty<>(approvalDate);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public int getCustomerId() { return customerId.get(); }
    public LocalDate getRequestDate() { return requestDate.get(); }
    public String getStatus() { return status.get(); }
    public int getApproverId() { return approverId.get(); }
    public LocalDate getApprovalDate() { return approvalDate.get(); }

    // Property metotları
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleIntegerProperty customerIdProperty() { return customerId; }
    public SimpleObjectProperty<LocalDate> requestDateProperty() { return requestDate; }
    public SimpleStringProperty statusProperty() { return status; }
    public SimpleIntegerProperty approverIdProperty() { return approverId; }
    public SimpleObjectProperty<LocalDate> approvalDateProperty() { return approvalDate; }
}