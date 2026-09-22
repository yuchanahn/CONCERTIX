package com.example.demo.queue;

public final class QueueExceptions {

    private QueueExceptions() {
    }

    public static class QueueConcertNotFoundException extends RuntimeException {
        public QueueConcertNotFoundException() {
            super("Published concert not found");
        }
    }

    public static class QueueAdmissionRequiredException extends RuntimeException {
        public QueueAdmissionRequiredException(String message) {
            super(message);
        }
    }
}
