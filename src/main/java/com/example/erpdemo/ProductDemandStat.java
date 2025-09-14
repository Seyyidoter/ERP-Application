package com.example.erpdemo;

/** Dashboard: ürün bazında bugünkü toplam talep miktarı */
public class ProductDemandStat {
    private final String productName;
    private final int totalQuantity;

    public ProductDemandStat(String productName, int totalQuantity) {
        this.productName = productName;
        this.totalQuantity = totalQuantity;
    }

    public String getProductName() { return productName; }
    public int getTotalQuantity() { return totalQuantity; }
}
