package com.example.erpdemo.exceptions;

/** Stok yetersizliği durumunu belirtir. */
public class InsufficientStockException extends DomainException {
    private static final long serialVersionUID = 1L;

    public InsufficientStockException() { super(); }
    public InsufficientStockException(String message) { super(message); }
    public InsufficientStockException(String message, Throwable cause) { super(message, cause); }
    public InsufficientStockException(Throwable cause) { super(cause); }
}
