package com.example.erpdemo.exceptions;

/** Uygulama-özel (domain) hataları için temel çalışma zamanı istisnası. */
public class DomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DomainException() { super(); }
    public DomainException(String message) { super(message); }
    public DomainException(String message, Throwable cause) { super(message, cause); }
    public DomainException(Throwable cause) { super(cause); }
}
