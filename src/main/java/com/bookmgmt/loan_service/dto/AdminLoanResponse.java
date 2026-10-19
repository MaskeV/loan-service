package com.bookmgmt.loan_service.dto;


import java.time.LocalDate;
import java.time.LocalDateTime;

// One row of the admin loans list: who borrowed which book, and whether it came back.
// status = BORROWED, OVERDUE (past the due date, not returned) or RETURNED
public record AdminLoanResponse(Long id, Long userId, String userName, String userEmail, Integer bookId,
                                String title, String author, String category, LocalDateTime borrowedAt, LocalDate dueDate,
                                LocalDateTime returnedAt, String status) {
}