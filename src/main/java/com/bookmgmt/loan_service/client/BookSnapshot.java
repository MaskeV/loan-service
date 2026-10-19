package com.bookmgmt.loan_service.client;

// What book-service returns after reserving a copy: just what a loan needs to remember.
public record BookSnapshot(Integer id, String title, String author, String category) {
}
