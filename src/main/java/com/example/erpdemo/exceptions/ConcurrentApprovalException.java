package com.example.erpdemo.exceptions;

/** Onay işlemlerinde yarış/çakışma (concurrency) durumlarını belirtir. */
public class ConcurrentApprovalException extends DomainException {
    private static final long serialVersionUID = 1L;

    public ConcurrentApprovalException() { super(); }
    public ConcurrentApprovalException(String message) { super(message); }
    public ConcurrentApprovalException(String message, Throwable cause) { super(message, cause); }
    public ConcurrentApprovalException(Throwable cause) { super(cause); }
}
