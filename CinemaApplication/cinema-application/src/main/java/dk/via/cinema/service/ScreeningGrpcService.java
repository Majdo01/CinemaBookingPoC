package dk.via.cinema.service;

import dk.via.cinema.entity.ReservationEntity;
import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.grpc.*;
import dk.via.cinema.repository.ReservationRepository;
import dk.via.cinema.repository.ScreeningRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class ScreeningGrpcService
    extends ScreeningServiceGrpc.ScreeningServiceImplBase {

  private static final Logger log =
      LoggerFactory.getLogger(ScreeningGrpcService.class);

  private static final String START_TIME_FORMAT_MESSAGE =
      "startTime must be a valid ISO-8601 local date-time, "
          + "for example 2026-12-05T21:00:00.";

  private final ScreeningRepository screeningRepository;
  private final ReservationRepository reservationRepository;

  public ScreeningGrpcService(
      ScreeningRepository screeningRepository,
      ReservationRepository reservationRepository) {
    this.screeningRepository = screeningRepository;
    this.reservationRepository = reservationRepository;
  }

  @Override
  public void listScreenings(
      EmptyRequest request,
      StreamObserver<ScreeningList> responseObserver) {

    try {
      ScreeningList.Builder result = ScreeningList.newBuilder();

      for (ScreeningEntity entity : screeningRepository.findAll()) {
        result.addScreenings(toProto(entity));
      }

      responseObserver.onNext(result.build());
      responseObserver.onCompleted();

    } catch (Exception exception) {
      log.error("Listing screenings failed", exception);
      responseObserver.onError(internal(
          "The screenings could not be loaded."));
    }
  }

  @Override
  public void createScreening(
      CreateScreeningRequest request,
      StreamObserver<CreateScreeningResponse> responseObserver) {

    String movieTitle = request.getMovieTitle().trim();
    String room = request.getRoom().trim();
    String startTimeValue = request.getStartTime().trim();

    if (movieTitle.isEmpty()) {
      reject(responseObserver, "movieTitle must not be empty.");
      return;
    }

    if (room.isEmpty()) {
      reject(responseObserver, "room must not be empty.");
      return;
    }

    if (startTimeValue.isEmpty()) {
      reject(responseObserver, START_TIME_FORMAT_MESSAGE);
      return;
    }

    LocalDateTime startTime;

    try {
      startTime = LocalDateTime.parse(startTimeValue);
    } catch (DateTimeParseException exception) {
      reject(responseObserver, START_TIME_FORMAT_MESSAGE);
      return;
    }

    try {
      ScreeningEntity savedEntity = screeningRepository.save(
          new ScreeningEntity(movieTitle, startTime, room));

      CreateScreeningResponse response = CreateScreeningResponse.newBuilder()
          .setSuccess(true)
          .setMessage("Screening created successfully.")
          .setScreening(toProto(savedEntity))
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception exception) {
      // The client only receives a generic message; the cause stays in the log.
      log.error("Creating a screening failed", exception);
      responseObserver.onError(internal(
          "The screening could not be saved."));
    }
  }

  @Override
  public void createReservation(
      CreateReservationRequest request,
      StreamObserver<CreateReservationResponse> responseObserver) {

    long screeningId = request.getScreeningId();

    // Seat labels are a row letter and a number ("A5"). Upper-casing keeps
    // "a5" from becoming a second reservation for the same physical seat.
    String seatNumber = request.getSeatNumber()
        .trim()
        .toUpperCase(Locale.ROOT);

    if (screeningId <= 0) {
      responseObserver.onError(Status.INVALID_ARGUMENT
          .withDescription("screeningId must be the id of an existing screening.")
          .asRuntimeException());
      return;
    }

    if (seatNumber.isEmpty()) {
      responseObserver.onError(Status.INVALID_ARGUMENT
          .withDescription("seatNumber must not be empty.")
          .asRuntimeException());
      return;
    }

    ScreeningEntity screening;

    try {
      screening = screeningRepository.findById(screeningId).orElse(null);
    } catch (Exception exception) {
      log.error("Loading screening {} for a reservation failed",
          screeningId, exception);
      responseObserver.onError(internal(
          "The screening could not be loaded."));
      return;
    }

    if (screening == null) {
      responseObserver.onError(Status.NOT_FOUND
          .withDescription("Screening " + screeningId + " does not exist.")
          .asRuntimeException());
      return;
    }

    String seatTakenMessage =
        "Seat " + seatNumber + " is already reserved for this screening.";

    try {
      // Fast feedback for the common case. A free seat is not a guarantee, so
      // the insert below still relies on the database constraint.
      if (reservationRepository.existsByScreening_IdAndSeatNumber(
          screeningId, seatNumber)) {
        responseObserver.onError(Status.ALREADY_EXISTS
            .withDescription(seatTakenMessage)
            .asRuntimeException());
        return;
      }

      ReservationEntity savedEntity = reservationRepository.save(
          new ReservationEntity(screening, seatNumber));

      CreateReservationResponse response = CreateReservationResponse
          .newBuilder()
          .setSuccess(true)
          .setMessage("Reservation created successfully.")
          .setReservation(toProto(savedEntity))
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (DataIntegrityViolationException exception) {
      // Another request reserved the same seat between the check and the
      // insert, and the unique constraint on (screening_id, seat_number)
      // refused it. That is a duplicate, not a server fault.
      log.warn("Reservation for screening {} seat {} was rejected by the "
              + "database constraint",
          screeningId, seatNumber, exception);
      responseObserver.onError(Status.ALREADY_EXISTS
          .withDescription(seatTakenMessage)
          .asRuntimeException());

    } catch (Exception exception) {
      // The client only receives a generic message; the cause stays in the log.
      log.error("Creating a reservation failed", exception);
      responseObserver.onError(internal(
          "The reservation could not be saved."));
    }
  }

  @Override
  public void getReservedSeats(
      ReservedSeatsRequest request,
      StreamObserver<ReservedSeatsResponse> responseObserver) {

    long screeningId = request.getScreeningId();

    if (screeningId <= 0) {
      responseObserver.onError(Status.INVALID_ARGUMENT
          .withDescription("screeningId must be the id of an existing screening.")
          .asRuntimeException());
      return;
    }

    try {
      if (!screeningRepository.existsById(screeningId)) {
        responseObserver.onError(Status.NOT_FOUND
            .withDescription("Screening " + screeningId + " does not exist.")
            .asRuntimeException());
        return;
      }
    } catch (Exception exception) {
      log.error("Checking screening {} for its reserved seats failed",
          screeningId, exception);
      responseObserver.onError(internal(
          "The screening could not be loaded."));
      return;
    }

    try {
      ReservedSeatsResponse.Builder result = ReservedSeatsResponse.newBuilder();

      for (ReservationEntity entity : reservationRepository
          .findByScreening_IdOrderByIdAsc(screeningId)) {
        result.addReservations(toProto(entity));
      }

      responseObserver.onNext(result.build());
      responseObserver.onCompleted();

    } catch (Exception exception) {
      log.error("Loading the reserved seats of screening {} failed",
          screeningId, exception);
      responseObserver.onError(internal(
          "The reserved seats could not be loaded."));
    }
  }

  private static Screening toProto(ScreeningEntity entity) {
    return Screening.newBuilder()
        .setId(entity.getId() == null ? 0L : entity.getId())
        .setMovieTitle(entity.getMovieTitle())
        .setStartTime(entity.getStartTime().toString())
        .setRoom(entity.getRoom())
        .build();
  }

  private static Reservation toProto(ReservationEntity entity) {
    Long screeningId = entity.getScreening() == null
        ? null
        : entity.getScreening().getId();

    return Reservation.newBuilder()
        .setId(entity.getId() == null ? 0L : entity.getId())
        .setScreeningId(screeningId == null ? 0L : screeningId)
        .setSeatNumber(entity.getSeatNumber())
        .build();
  }

  private static void reject(
      StreamObserver<CreateScreeningResponse> responseObserver,
      String description) {
    responseObserver.onError(Status.INVALID_ARGUMENT
        .withDescription(description)
        .asRuntimeException());
  }

  private static RuntimeException internal(String description) {
    return Status.INTERNAL
        .withDescription(description)
        .asRuntimeException();
  }
}
