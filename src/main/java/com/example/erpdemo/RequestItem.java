package com.example.erpdemo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Talep kalemi: miktar, liste fiyatı, iskontolu fiyat ve ara toplam. */
public class RequestItem {
    private final int id;
    private final int requestId;
    private final int productId;
    private final String productName;
    private final int quantity;

    /** Liste fiyatı (iskontosuz, 2 ondalık) */
    private final BigDecimal listPrice;

    /** İskontolu birim fiyat (2 ondalık) */
    private final BigDecimal discountedPrice;

    // BigDecimal temelli ana kurucu
    public RequestItem(int id,
                       int requestId,
                       int productId,
                       String productName,
                       int quantity,
                       BigDecimal listPrice,
                       BigDecimal discountedPrice) {
        this.id = id;
        this.requestId = requestId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        // null güvenliği + tutarlı ölçek
        this.listPrice = scale2(listPrice);
        this.discountedPrice = scale2(discountedPrice);
    }

    // (Opsiyonel) eski çağrılar için double → BigDecimal sarmalayıcı
    @Deprecated
    public RequestItem(int id, int requestId, int productId, String productName, int quantity,
                       double listPrice, double discountedPrice) {
        this(id, requestId, productId, productName, quantity,
                BigDecimal.valueOf(listPrice), BigDecimal.valueOf(discountedPrice));
    }

    public int getId() { return id; }
    public int getRequestId() { return requestId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }

    /** Liste fiyatı (TL, 2 ondalık) */
    public BigDecimal getListPrice() { return listPrice; }

    /** İskontolu birim fiyat (TL, 2 ondalık) */
    public BigDecimal getDiscountedPrice() { return discountedPrice; }

    /** Ara toplam = miktar * iskontolu fiyat (2 ondalık, HALF_UP) */
    public BigDecimal getSubtotal() {
        return discountedPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** null → 0.00; aksi halde 2 ondalık HALF_UP */
    private static BigDecimal scale2(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
