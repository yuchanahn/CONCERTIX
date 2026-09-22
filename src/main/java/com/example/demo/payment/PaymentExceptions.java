package com.example.demo.payment;

public final class PaymentExceptions {

    private PaymentExceptions() {
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException() {
            super("Payment or reservation not found");
        }
    }

    public static class PaymentAccessDeniedException extends RuntimeException {
        public PaymentAccessDeniedException() {
            super("Access denied to payment");
        }
    }

    public static class PaymentConflictException extends RuntimeException {
        public PaymentConflictException(String message) {
            super(message);
        }
    }
}
