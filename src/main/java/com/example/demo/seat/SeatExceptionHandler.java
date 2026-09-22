package com.example.demo.seat;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.demo.concert.ConcertAlreadyPublishedException;

@RestControllerAdvice(assignableTypes = SeatController.class)
class SeatExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void badRequest() {
    }

    @ExceptionHandler(ConcertAlreadyPublishedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void conflict() {
    }
}
