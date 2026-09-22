package com.example.demo.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.demo.reservation.ReservationExceptions.BookingPeriodClosedException;
import com.example.demo.reservation.ReservationExceptions.ConcertNotPublishedException;
import com.example.demo.reservation.ReservationExceptions.MaxTicketsExceededException;
import com.example.demo.reservation.ReservationExceptions.ReservationAccessDeniedException;
import com.example.demo.reservation.ReservationExceptions.ReservationAlreadyCanceledException;
import com.example.demo.reservation.ReservationExceptions.ReservationNotFoundException;
import com.example.demo.reservation.ReservationExceptions.SeatNotAvailableException;
import com.example.demo.queue.QueueExceptions.QueueAdmissionRequiredException;

@RestControllerAdvice(assignableTypes = ReservationController.class)
class ReservationExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void badRequest() {
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {
    }

    @ExceptionHandler({ ReservationAccessDeniedException.class, QueueAdmissionRequiredException.class })
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {
    }

    @ExceptionHandler({
            SeatNotAvailableException.class,
            BookingPeriodClosedException.class,
            ConcertNotPublishedException.class,
            MaxTicketsExceededException.class,
            ReservationAlreadyCanceledException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    void conflict() {
    }
}
