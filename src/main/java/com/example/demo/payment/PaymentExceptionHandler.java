package com.example.demo.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.demo.payment.PaymentExceptions.PaymentAccessDeniedException;
import com.example.demo.payment.PaymentExceptions.PaymentConflictException;
import com.example.demo.payment.PaymentExceptions.PaymentNotFoundException;

@RestControllerAdvice(assignableTypes = PaymentController.class)
class PaymentExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void badRequest() {
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {
    }

    @ExceptionHandler(PaymentAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {
    }

    @ExceptionHandler(PaymentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void conflict() {
    }
}
