package com.example.erpdemo;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Product {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty urunAdi;
    private final SimpleDoubleProperty fiyat;
    private final SimpleIntegerProperty stok;
    private final SimpleStringProperty birim;

    public Product(int id, String urunAdi, double fiyat, int stok, String birim) {
        this.id = new SimpleIntegerProperty(id);
        this.urunAdi = new SimpleStringProperty(urunAdi);
        this.fiyat = new SimpleDoubleProperty(fiyat);
        this.stok = new SimpleIntegerProperty(stok);
        this.birim = new SimpleStringProperty(birim);
    }

    // Getter metotları
    public int getId() { return id.get(); }
    public String getUrunAdi() { return urunAdi.get(); }
    public double getFiyat() { return fiyat.get(); }
    public int getStok() { return stok.get(); }
    public String getBirim() { return birim.get(); }

    // Property metotları (JavaFX için)
    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty urunAdiProperty() { return urunAdi; }
    public SimpleDoubleProperty fiyatProperty() { return fiyat; }
    public SimpleIntegerProperty stokProperty() { return stok; }
    public SimpleStringProperty birimProperty() { return birim; }

    // Setter metotları
    public void setUrunAdi(String urunAdi) { this.urunAdi.set(urunAdi); }
    public void setFiyat(double fiyat) { this.fiyat.set(fiyat); }
    public void setStok(int stok) { this.stok.set(stok); }
    public void setBirim(String birim) { this.birim.set(birim); }
}