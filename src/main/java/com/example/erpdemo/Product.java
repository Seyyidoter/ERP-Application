package com.example.erpdemo;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Product {

    private final SimpleIntegerProperty id;
    private final SimpleStringProperty  urunAdi;
    private final ObjectProperty<BigDecimal> fiyat;  // BigDecimal
    private final SimpleIntegerProperty stok;
    private final SimpleStringProperty  birim;

    public Product(int id, String urunAdi, BigDecimal fiyat, int stok, String birim) {
        this.id = new SimpleIntegerProperty(id);
        this.urunAdi = new SimpleStringProperty(norm(urunAdi));
        this.fiyat = new SimpleObjectProperty<>(fiyat == null ? BigDecimal.ZERO : fiyat.setScale(2, RoundingMode.HALF_UP));
        this.stok = new SimpleIntegerProperty(stok);
        this.birim = new SimpleStringProperty(norm(birim));
    }

    public int getId() { return id.get(); }
    public String getUrunAdi() { return urunAdi.get(); }
    public BigDecimal getFiyat() { return fiyat.get(); }
    public int getStok() { return stok.get(); }
    public String getBirim() { return birim.get(); }

    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty urunAdiProperty() { return urunAdi; }
    public ObjectProperty<BigDecimal> fiyatProperty() { return fiyat; }
    public SimpleIntegerProperty stokProperty() { return stok; }
    public SimpleStringProperty birimProperty() { return birim; }

    public void setUrunAdi(String urunAdi) { this.urunAdi.set(norm(urunAdi)); }
    public void setFiyat(BigDecimal fiyat) {
        this.fiyat.set((fiyat == null ? BigDecimal.ZERO : fiyat.setScale(2, RoundingMode.HALF_UP)));
    }
    public void setStok(int stok) { this.stok.set(stok); }
    public void setBirim(String birim) { this.birim.set(norm(birim)); }

    /** null → "", trim → iç/dış boşluk sadeleştirme */
    private static String norm(String s) {
        if (s == null) return "";
        // dışı kırp, içerdeki birden fazla boşluğu tek boşluğa indir (isteğe bağlı faydalı)
        String t = s.trim().replaceAll("\\s+", " ");
        return t;
    }
}
