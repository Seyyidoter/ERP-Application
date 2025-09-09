package com.example.erpdemo;
import javafx.util.StringConverter;
public class ProductStringConverter extends StringConverter<Product> {
    @Override
    public String toString(Product product) {
        return product != null ? product.getUrunAdi() : "";
    }
    @Override
    public Product fromString(String s) {
        return null;
    }
}