package com.bookmgmt.loan_service.dto;


public record LoanStats(long total, long active, long overdue, long returned) {}
