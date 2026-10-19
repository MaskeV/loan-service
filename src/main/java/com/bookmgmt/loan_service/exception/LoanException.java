package com.bookmgmt.loan_service.exception;



import org.springframework.http.HttpStatus;

// A business-rule failure with the HTTP status the client should receive
public class LoanException extends RuntimeException {

    private final HttpStatus status;

    public LoanException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
