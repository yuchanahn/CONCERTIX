package com.example.demo.queue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.queue.QueueExceptions.QueueAdmissionRequiredException;
import com.example.demo.queue.QueueExceptions.QueueConcertNotFoundException;

@RestControllerAdvice(assignableTypes = { QueueController.class, AdminQueueController.class })
class QueueExceptionHandler {

    @ExceptionHandler(QueueConcertNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {
    }

    @ExceptionHandler(QueueAdmissionRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void forbidden() {
    }
}
