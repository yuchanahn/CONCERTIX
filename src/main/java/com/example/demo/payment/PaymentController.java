package com.example.demo.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.auth.JwtTokenProvider.AccessPrincipal;
import com.example.demo.payment.PaymentDtos.CompleteRequest;
import com.example.demo.payment.PaymentDtos.CreateRequest;
import com.example.demo.payment.PaymentDtos.Response;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(
            @AuthenticationPrincipal AccessPrincipal principal,
            @Valid @RequestBody CreateRequest request) {
        return paymentService.create(principal.memberId(), request);
    }

    @GetMapping("/{paymentId}")
    public Response get(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID paymentId) {
        return paymentService.get(principal.memberId(), paymentId);
    }

    @PostMapping("/{paymentId}/complete")
    public Response complete(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID paymentId,
            @Valid @RequestBody CompleteRequest request) {
        return paymentService.complete(principal.memberId(), paymentId, request);
    }

    @PostMapping("/{paymentId}/cancel")
    public Response cancel(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID paymentId) {
        return paymentService.cancel(principal.memberId(), paymentId);
    }
}
