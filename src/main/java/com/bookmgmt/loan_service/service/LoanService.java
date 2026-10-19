package com.bookmgmt.loan_service.service;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.bookmgmt.loan_service.client.BookClient;
import com.bookmgmt.loan_service.client.BookSnapshot;
import com.bookmgmt.loan_service.dto.LoanResponse;
import com.bookmgmt.loan_service.entity.Loan;
import com.bookmgmt.loan_service.exception.LoanException;
import com.bookmgmt.loan_service.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    public static final int MAX_ACTIVE_LOANS = 5; // books one user may hold at the same time
    public static final int LOAN_DAYS = 14;       // days until a book is due

    private final LoanRepository loanRepository;
    private final BookClient bookClient;

    public LoanService(LoanRepository loanRepository, BookClient bookClient) {
        this.loanRepository = loanRepository;
        this.bookClient = bookClient;
    }

    // Not @Transactional on purpose: we must not hold a DB connection while calling book-service,
    // and the compensation (release) has to run after the failed save, not inside its transaction.
    //
    // 1) check our own rules (local DB only)
    // 2) ask book-service to take one copy (atomic on its side)
    // 3) save the loan; if that fails, give the copy back
    public LoanResponse borrow(String email, String userName, Integer bookId) {
        if (loanRepository.existsByUserEmailAndBookIdAndReturnedAtIsNull(email, bookId))
            throw new LoanException(HttpStatus.CONFLICT, "You have already borrowed this book");
        if (loanRepository.countByUserEmailAndReturnedAtIsNullAndDueDateBefore(email, LocalDate.now()) > 0)
            throw new LoanException(HttpStatus.CONFLICT,
                    "You have overdue books. Please return them before borrowing more.");
        if (loanRepository.countByUserEmailAndReturnedAtIsNull(email) >= MAX_ACTIVE_LOANS)
            throw new LoanException(HttpStatus.CONFLICT,
                    "You can borrow at most " + MAX_ACTIVE_LOANS + " books at a time. Return one first.");

        BookSnapshot book = bookClient.reserve(bookId); // throws 404 / 409 / 503

        try {
            Loan loan = new Loan();
            loan.setUserEmail(email);
            loan.setUserName(userName);
            loan.setBookId(book.id());
            loan.setBookTitle(book.title());
            loan.setBookAuthor(book.author());
            loan.setBookCategory(book.category());
            loan.setBorrowedAt(LocalDateTime.now());
            loan.setDueDate(LocalDate.now().plusDays(LOAN_DAYS));
            return toResponse(loanRepository.save(loan));
        } catch (RuntimeException e) {
            compensateRelease(bookId, e);
            throw e;
        }
    }

    // Mark the loan returned first, then give the copy back. If book-service cannot be reached,
    // undo the mark so the user can simply retry and the copy is not lost.
    public LoanResponse giveBack(String email, Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanException(HttpStatus.NOT_FOUND, "Loan not found"));
        if (!loan.getUserEmail().equalsIgnoreCase(email))
            throw new LoanException(HttpStatus.FORBIDDEN, "This loan belongs to another user");
        if (loan.getReturnedAt() != null)
            throw new LoanException(HttpStatus.CONFLICT, "This book was already returned");

        loan.setReturnedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);
        try {
            bookClient.release(loan.getBookId());
        } catch (RuntimeException e) {
            loan.setReturnedAt(null);
            loanRepository.save(loan);
            throw e;
        }
        return toResponse(loan);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> myLoans(String email) {
        return loanRepository.findByUserEmailOrderByBorrowedAtDesc(email).stream().map(this::toResponse).toList();
    }

    // Admin view: who borrowed what, and whether it has been returned
    @Transactional(readOnly = true)
    public List<LoanResponse> allLoans() {
        return loanRepository.findAllByOrderByBorrowedAtDesc().stream().map(this::toResponse).toList();
    }

    private void compensateRelease(Integer bookId, RuntimeException original) {
        try {
            bookClient.release(bookId);
        } catch (RuntimeException releaseFailure) {
            // A copy stays reserved without a loan row. Log loudly so it can be fixed by hand.
            log.error("Loan save failed ({}) AND releasing book {} failed - stock is one copy short",
                    original.getMessage(), bookId, releaseFailure);
        }
    }

    private LoanResponse toResponse(Loan l) {
        String status = l.getReturnedAt() != null ? "RETURNED"
                : l.getDueDate().isBefore(LocalDate.now()) ? "OVERDUE" : "BORROWED";
        return new LoanResponse(l.getId(), l.getBookId(), l.getBookTitle(), l.getBookAuthor(), l.getBookCategory(),
                l.getBorrowedAt(), l.getDueDate(), l.getReturnedAt(), status, l.getUserName(), l.getUserEmail());
    }
}