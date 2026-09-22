package com.example.demo.reservation;

public class ReservationExceptions {

    public static class SeatNotAvailableException extends RuntimeException {
        public SeatNotAvailableException(String message) {
            super(message);
        }
    }

    public static class BookingPeriodClosedException extends RuntimeException {
        public BookingPeriodClosedException(String message) {
            super(message);
        }
    }

    public static class ConcertNotPublishedException extends RuntimeException {
        public ConcertNotPublishedException() {
            super("Concert is not published");
        }
    }

    public static class MaxTicketsExceededException extends RuntimeException {
        public MaxTicketsExceededException(String message) {
            super(message);
        }
    }

    public static class ReservationNotFoundException extends RuntimeException {
        public ReservationNotFoundException() {
            super("Reservation not found");
        }
    }

    public static class ReservationAccessDeniedException extends RuntimeException {
        public ReservationAccessDeniedException() {
            super("Access denied to reservation");
        }
    }

    public static class ReservationAlreadyCanceledException extends RuntimeException {
        public ReservationAlreadyCanceledException(String message) {
            super(message);
        }
    }
}
