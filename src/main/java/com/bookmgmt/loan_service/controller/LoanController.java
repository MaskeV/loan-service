package com.bookmgmt.loan_service.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.bookmgmt.loan_service.dto.LoanResponse;
import com.bookmgmt.loan_service.exception.LoanException;
import com.bookmgmt.loan_service.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// No Spring Security in this service. The API gateway validates the JWT and forwards who the caller is:
//   X-User-Email  (JWT subject)   X-User-Role  (USER / ADMIN)   X-User-Name  (URL-encoded)
// so this service must only be reachable through the gateway.
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/{bookId}")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@PathVariable Integer bookId,
                               @RequestHeader("X-User-Email") String email,
                               @RequestHeader(value = "X-User-Name", required = false) String encodedName) {
        return loanService.borrow(email, decode(encodedName, email), bookId);
    }

    @PostMapping("/{loanId}/return")
    public LoanResponse giveBack(@PathVariable Long loanId, @RequestHeader("X-User-Email") String email) {
        return loanService.giveBack(email, loanId);
    }

    @GetMapping("/my")
    public List<LoanResponse> myLoans(@RequestHeader("X-User-Email") String email) {
        return loanService.myLoans(email);
    }

    // Every loan of every user. The gateway should only let ADMIN reach this route; the one-line
    // header check below is a cheap safety net in case that rule is ever missed. Delete it if you
    // want the gateway to be the only place that decides.
    @GetMapping
    public List<LoanResponse> allLoans(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role))
            throw new LoanException(HttpStatus.FORBIDDEN, "Admin only");
        return loanService.allLoans();
    }

    private String decode(String encodedName, String fallback) {
        if (encodedName == null || encodedName.isBlank())
            return fallback;
        try {
            return URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return encodedName;
        }
    }
}