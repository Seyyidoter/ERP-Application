package com.example.erpdemo;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class RequestItem {

    private final SimpleIntegerProperty id;
    private final SimpleIntegerProperty requestId;
    private final SimpleIntegerProperty productId;

    private final SimpleStringProperty productName; // Bu alan eklendi
    private final SimpleIntegerProperty quantity;
    private final SimpleDoubleProperty price;
    private final SimpleDoubleProperty discountedPrice;

    public RequestItem(int id, int requestId, int productId, String productName, int quantity, double price, double discountedPrice) {
        this.id = new SimpleIntegerProperty(id);
        this.requestId = new SimpleIntegerProperty(requestId);
        this.productId = new SimpleIntegerProperty(productId);
        this.productName = new SimpleStringProperty(productName);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.price = new SimpleDoubleProperty(price);
        this.discountedPrice = new SimpleDoubleProperty(discountedPrice);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public int getRequestId() { return requestId.get(); }
    public int getProductId() { return productId.get(); }
    public String getProductName() { return productName.get(); }
    public int getQuantity() { return quantity.get(); }
    public double getPrice() { return price.get(); }
    public double getDiscountedPrice() { return discountedPrice.get(); }

    // Property metotları
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleIntegerProperty requestIdProperty() { return requestId; }
    public SimpleIntegerProperty productIdProperty() { return productId; }
    public SimpleStringProperty productNameProperty() { return productName; }
    public SimpleIntegerProperty quantityProperty() { return quantity; }
    public SimpleDoubleProperty priceProperty() { return price; }
    public SimpleDoubleProperty discountedPriceProperty() { return discountedPrice; }
}