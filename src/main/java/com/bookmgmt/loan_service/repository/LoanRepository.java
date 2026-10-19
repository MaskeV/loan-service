package com.bookmgmt.loan_service.repository;



import java.time.LocalDate;
import java.util.List;

import com.bookmgmt.loan_service.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;



// Plain derived queries on the loans table only - no joins to users or books.
public interface LoanRepository extends JpaRepository<Loan, Long> {

    // a user's loans, newest first
    List<Loan> findByUserEmailOrderByBorrowedAtDesc(String userEmail);

    // every loan of every user, newest first (admin view)
    List<Loan> findAllByOrderByBorrowedAtDesc();

    // books the user still holds
    long countByUserEmailAndReturnedAtIsNull(String userEmail);

    // does the user already hold this book?
    boolean existsByUserEmailAndBookIdAndReturnedAtIsNull(String userEmail, Integer bookId);

    // books the user still holds that were due before today
    long countByUserEmailAndReturnedAtIsNullAndDueDateBefore(String userEmail, LocalDate today);
}