package com.bookmgmt.loan_service.dto;



import java.time.LocalDate;
import java.time.LocalDateTime;

// What the API sends for a loan.
// status = BORROWED, OVERDUE (past the due date, not returned) or RETURNED
// userName / userEmail say who borrowed the book (used by the admin loans list).
public record LoanResponse(Long id, Integer bookId, String title, String author, String category,
                           LocalDateTime borrowedAt, LocalDate dueDate, LocalDateTime returnedAt, String status,
                           String userName, String userEmail) {
}