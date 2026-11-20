package dk.via.cinema.service;

import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.grpc.*;
import dk.via.cinema.repository.ScreeningRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Service
public class ScreeningGrpcService
    extends ScreeningServiceGrpc.ScreeningServiceImplBase {

  private static final Logger log =
      LoggerFactory.getLogger(ScreeningGrpcService.class);

  private static final String START_TIME_FORMAT_MESSAGE =
      "startTime must be a valid ISO-8601 local date-time, "
          + "for example 2026-12-05T21:00:00.";

  private final ScreeningRepository screeningRepository;

  public ScreeningGrpcService(
      ScreeningRepository screeningRepository) {
    this.screeningRepository = screeningRepository;
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

  private static Screening toProto(ScreeningEntity entity) {
    return Screening.newBuilder()
        .setId(entity.getId() == null ? 0L : entity.getId())
        .setMovieTitle(entity.getMovieTitle())
        .setStartTime(entity.getStartTime().toString())
        .setRoom(entity.getRoom())
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
