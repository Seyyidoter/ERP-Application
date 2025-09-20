package com.example.erpdemo;

import java.math.BigDecimal;

/** Talep kalemi: miktar, liste fiyatı, iskontolu fiyat ve ara toplam. */
public class RequestItem {
    private final int id;
    private final int requestId;
    private final int productId;
    private final String productName;
    private final int quantity;
    /** Liste fiyatı (iskontosuz) */
    private final BigDecimal listPrice;
    /** İskontolu birim fiyat */
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
        this.listPrice = listPrice == null ? BigDecimal.ZERO : listPrice;
        this.discountedPrice = discountedPrice == null ? BigDecimal.ZERO : discountedPrice;
    }

    // (Opsiyonel) eski çağrılar için double → BigDecimal sarmalayıcı
    @Deprecated
    public RequestItem(int id, int requestId, int productId, String productName, int quantity, double listPrice, double discountedPrice) {
        this(id, requestId, productId, productName, quantity, BigDecimal.valueOf(listPrice), BigDecimal.valueOf(discountedPrice));
    }

    public int getId() { return id; }
    public int getRequestId() { return requestId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }

    /** Liste fiyatı (TL) */
    public BigDecimal getListPrice() { return listPrice; }

    /** İskontolu birim fiyat (TL) */
    public BigDecimal getDiscountedPrice() { return discountedPrice; }

    /** Ara toplam = miktar * iskontolu fiyat */
    public BigDecimal getSubtotal() {
        return discountedPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
