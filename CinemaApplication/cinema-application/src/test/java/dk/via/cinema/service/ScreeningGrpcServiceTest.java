package dk.via.cinema.service;

import dk.via.cinema.entity.ReservationEntity;
import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.grpc.CreateReservationRequest;
import dk.via.cinema.grpc.CreateReservationResponse;
import dk.via.cinema.grpc.CreateScreeningRequest;
import dk.via.cinema.grpc.CreateScreeningResponse;
import dk.via.cinema.grpc.EmptyRequest;
import dk.via.cinema.grpc.ReservedSeatsRequest;
import dk.via.cinema.grpc.ReservedSeatsResponse;
import dk.via.cinema.grpc.ScreeningList;
import dk.via.cinema.repository.ReservationRepository;
import dk.via.cinema.repository.ScreeningRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningGrpcServiceTest {

  @Mock
  private ScreeningRepository screeningRepository;

  @Mock
  private ReservationRepository reservationRepository;

  @InjectMocks
  private ScreeningGrpcService screeningGrpcService;

  @Test
  void createScreeningPersistsScreeningAndReturnsGeneratedId() {
    when(screeningRepository.save(any(ScreeningEntity.class)))
        .thenAnswer(invocation -> {
          ScreeningEntity entity = invocation.getArgument(0);
          ReflectionTestUtils.setField(entity, "id", 42L);
          return entity;
        });

    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Dune: Part Two", "2026-12-05T21:00:00", "Sal 2"),
        observer);

    assertNull(observer.error, "successful creation must not report an error");
    assertTrue(observer.completed, "the response must be completed");
    assertEquals(1, observer.values.size());

    CreateScreeningResponse response = observer.values.get(0);
    assertTrue(response.getSuccess());
    assertEquals("Screening created successfully.", response.getMessage());
    assertEquals(42L, response.getScreening().getId());
    assertEquals("Dune: Part Two", response.getScreening().getMovieTitle());
    assertEquals("2026-12-05T21:00", response.getScreening().getStartTime());
    assertEquals("Sal 2", response.getScreening().getRoom());

    ArgumentCaptor<ScreeningEntity> captor =
        ArgumentCaptor.forClass(ScreeningEntity.class);
    verify(screeningRepository).save(captor.capture());

    ScreeningEntity saved = captor.getValue();
    assertEquals("Dune: Part Two", saved.getMovieTitle());
    assertEquals(LocalDateTime.of(2026, 12, 5, 21, 0), saved.getStartTime());
    assertEquals("Sal 2", saved.getRoom());
  }

  @Test
  void createScreeningTrimsValuesBeforePersisting() {
    when(screeningRepository.save(any(ScreeningEntity.class)))
        .thenAnswer(invocation -> {
          ScreeningEntity entity = invocation.getArgument(0);
          ReflectionTestUtils.setField(entity, "id", 7L);
          return entity;
        });

    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("  Perfect Days  ", "2026-12-05T21:00:00", "  Sal 1 "),
        observer);

    ArgumentCaptor<ScreeningEntity> captor =
        ArgumentCaptor.forClass(ScreeningEntity.class);
    verify(screeningRepository).save(captor.capture());

    assertEquals("Perfect Days", captor.getValue().getMovieTitle());
    assertEquals("Sal 1", captor.getValue().getRoom());
  }

  @Test
  void createScreeningWithMalformedStartTimeReportsInvalidArgument() {
    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Perfect Days", "tomorrow evening", "Sal 2"),
        observer);

    assertTrue(observer.values.isEmpty(), "a rejected request must not produce a response");
    assertFalse(observer.completed);
    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("startTime"));

    verifyNoInteractions(screeningRepository);
  }

  @Test
  void createScreeningWithEmptyMovieTitleReportsInvalidArgument() {
    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("   ", "2026-12-05T21:00:00", "Sal 2"),
        observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("movieTitle"));

    verifyNoInteractions(screeningRepository);
  }

  @Test
  void createScreeningWithEmptyRoomReportsInvalidArgument() {
    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Perfect Days", "2026-12-05T21:00:00", "  "),
        observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("room"));

    verifyNoInteractions(screeningRepository);
  }

  @Test
  void createScreeningWithEmptyStartTimeReportsInvalidArgument() {
    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Perfect Days", "", "Sal 2"),
        observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());

    verifyNoInteractions(screeningRepository);
  }

  @Test
  void createScreeningWithStartTimeWithOffsetReportsInvalidArgument() {
    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Perfect Days", "2026-12-05T21:00:00Z", "Sal 2"),
        observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());

    verifyNoInteractions(screeningRepository);
  }

  @Test
  void createScreeningReportsInternalErrorWhenRepositoryFails() {
    when(screeningRepository.save(any(ScreeningEntity.class)))
        .thenThrow(new DataAccessResourceFailureException(
            "connection refused by database server"));

    CapturingObserver<CreateScreeningResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createScreening(
        request("Perfect Days", "2026-12-05T21:00:00", "Sal 2"),
        observer);

    StatusRuntimeException error = observer.status();
    assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
    assertEquals("The screening could not be saved.", error.getStatus().getDescription());

    // Internal details must never reach the client.
    assertFalse(error.getStatus().getDescription()
        .contains("connection refused"));
    assertFalse(observer.completed);
  }

  @Test
  void listScreeningsReturnsAllPersistedScreenings() {
    ScreeningEntity first = entity(1L, "Dune: Part Two",
        LocalDateTime.of(2026, 1, 10, 18, 0), "Sal 1");
    ScreeningEntity second = entity(2L, "Oppenheimer",
        LocalDateTime.of(2026, 1, 10, 21, 0), "Sal 3");

    when(screeningRepository.findAll()).thenReturn(List.of(first, second));

    CapturingObserver<ScreeningList> observer = new CapturingObserver<>();

    screeningGrpcService.listScreenings(EmptyRequest.getDefaultInstance(), observer);

    assertNull(observer.error);
    assertTrue(observer.completed);
    assertEquals(1, observer.values.size());

    ScreeningList list = observer.values.get(0);
    assertEquals(2, list.getScreeningsCount());
    assertEquals("Dune: Part Two", list.getScreenings(0).getMovieTitle());
    assertEquals("2026-01-10T18:00", list.getScreenings(0).getStartTime());
    assertEquals("Sal 3", list.getScreenings(1).getRoom());
  }

  @Test
  void listScreeningsReportsInternalErrorWhenRepositoryFails() {
    when(screeningRepository.findAll())
        .thenThrow(new DataAccessResourceFailureException("database offline"));

    CapturingObserver<ScreeningList> observer = new CapturingObserver<>();

    screeningGrpcService.listScreenings(EmptyRequest.getDefaultInstance(), observer);

    StatusRuntimeException error = observer.status();
    assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
    assertFalse(error.getStatus().getDescription().contains("database offline"));
    assertFalse(observer.completed);
  }

  @Test
  void createReservationPersistsReservationForTheRequestedScreening() {
    ScreeningEntity screening = screening(10L);

    when(screeningRepository.findById(10L)).thenReturn(Optional.of(screening));
    when(reservationRepository.existsByScreening_IdAndSeatNumber(10L, "A5"))
        .thenReturn(false);
    when(reservationRepository.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> {
          ReservationEntity entity = invocation.getArgument(0);
          ReflectionTestUtils.setField(entity, "id", 77L);
          return entity;
        });

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "A5"), observer);

    assertNull(observer.error, "successful creation must not report an error");
    assertTrue(observer.completed, "the response must be completed");
    assertEquals(1, observer.values.size());

    CreateReservationResponse response = observer.values.get(0);
    assertTrue(response.getSuccess());
    assertEquals("Reservation created successfully.", response.getMessage());
    assertEquals(77L, response.getReservation().getId());
    assertEquals(10L, response.getReservation().getScreeningId());
    assertEquals("A5", response.getReservation().getSeatNumber());

    ArgumentCaptor<ReservationEntity> captor =
        ArgumentCaptor.forClass(ReservationEntity.class);
    verify(reservationRepository).save(captor.capture());

    ReservationEntity saved = captor.getValue();
    assertNotNull(saved.getScreening());
    assertEquals(10L, saved.getScreening().getId(),
        "the reservation must belong to the requested screening");
    assertEquals("A5", saved.getSeatNumber());
  }

  @Test
  void createReservationTrimsAndUpperCasesTheSeatNumber() {
    when(screeningRepository.findById(10L)).thenReturn(Optional.of(screening(10L)));
    when(reservationRepository.existsByScreening_IdAndSeatNumber(10L, "A5"))
        .thenReturn(false);
    when(reservationRepository.save(any(ReservationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "  a5 "), observer);

    assertNull(observer.error);

    ArgumentCaptor<ReservationEntity> captor =
        ArgumentCaptor.forClass(ReservationEntity.class);
    verify(reservationRepository).save(captor.capture());

    assertEquals("A5", captor.getValue().getSeatNumber());
  }

  @Test
  void createReservationForAnOccupiedSeatReportsAlreadyExists() {
    when(screeningRepository.findById(10L)).thenReturn(Optional.of(screening(10L)));
    when(reservationRepository.existsByScreening_IdAndSeatNumber(10L, "A5"))
        .thenReturn(true);

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "A5"), observer);

    assertTrue(observer.values.isEmpty(), "a rejected request must not produce a response");
    assertFalse(observer.completed);
    assertEquals(Status.Code.ALREADY_EXISTS, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("A5"));

    verify(reservationRepository, never()).save(any(ReservationEntity.class));
  }

  @Test
  void createReservationForAnUnknownScreeningReportsNotFound() {
    when(screeningRepository.findById(99L)).thenReturn(Optional.empty());

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(99L, "A5"), observer);

    assertEquals(Status.Code.NOT_FOUND, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("99"));

    verifyNoInteractions(reservationRepository);
  }

  @Test
  void createReservationReportsAlreadyExistsWhenTheUniqueConstraintRejectsTheInsert() {
    // Simulates a simultaneous request that reserved the seat between this
    // request's pre-check and its insert.
    when(screeningRepository.findById(10L)).thenReturn(Optional.of(screening(10L)));
    when(reservationRepository.existsByScreening_IdAndSeatNumber(10L, "A5"))
        .thenReturn(false);
    when(reservationRepository.save(any(ReservationEntity.class)))
        .thenThrow(new DataIntegrityViolationException(
            "duplicate key value violates unique constraint "
                + "\"uq_reservations_screening_seat\""));

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "A5"), observer);

    StatusRuntimeException error = observer.status();
    assertEquals(Status.Code.ALREADY_EXISTS, error.getStatus().getCode());
    assertTrue(error.getStatus().getDescription().contains("A5"));

    // Database details must never reach the client.
    assertFalse(error.getStatus().getDescription().contains("duplicate key"));
    assertFalse(observer.completed);
  }

  @Test
  void createReservationWithEmptySeatNumberReportsInvalidArgument() {
    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "   "), observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("seatNumber"));

    verifyNoInteractions(screeningRepository, reservationRepository);
  }

  @Test
  void createReservationWithMissingScreeningIdReportsInvalidArgument() {
    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(0L, "A5"), observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("screeningId"));

    verifyNoInteractions(screeningRepository, reservationRepository);
  }

  @Test
  void createReservationReportsInternalErrorWhenRepositoryFails() {
    when(screeningRepository.findById(10L)).thenReturn(Optional.of(screening(10L)));
    when(reservationRepository.existsByScreening_IdAndSeatNumber(10L, "A5"))
        .thenReturn(false);
    when(reservationRepository.save(any(ReservationEntity.class)))
        .thenThrow(new DataAccessResourceFailureException(
            "connection refused by database server"));

    CapturingObserver<CreateReservationResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.createReservation(
        reservationRequest(10L, "A5"), observer);

    StatusRuntimeException error = observer.status();
    assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
    assertEquals("The reservation could not be saved.",
        error.getStatus().getDescription());
    assertFalse(error.getStatus().getDescription().contains("connection refused"));
    assertFalse(observer.completed);
  }

  @Test
  void getReservedSeatsReturnsTheReservationsOfTheScreening() {
    ScreeningEntity screening = screening(10L);

    when(screeningRepository.existsById(10L)).thenReturn(true);
    when(reservationRepository.findByScreening_IdOrderByIdAsc(10L))
        .thenReturn(List.of(
            reservation(5L, screening, "A1"),
            reservation(6L, screening, "B2")));

    CapturingObserver<ReservedSeatsResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.getReservedSeats(
        ReservedSeatsRequest.newBuilder().setScreeningId(10L).build(),
        observer);

    assertNull(observer.error);
    assertTrue(observer.completed);
    assertEquals(1, observer.values.size());

    ReservedSeatsResponse response = observer.values.get(0);
    assertEquals(2, response.getReservationsCount());
    assertEquals(5L, response.getReservations(0).getId());
    assertEquals(10L, response.getReservations(0).getScreeningId());
    assertEquals("A1", response.getReservations(0).getSeatNumber());
    assertEquals("B2", response.getReservations(1).getSeatNumber());
  }

  @Test
  void getReservedSeatsForAnUnknownScreeningReportsNotFound() {
    when(screeningRepository.existsById(99L)).thenReturn(false);

    CapturingObserver<ReservedSeatsResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.getReservedSeats(
        ReservedSeatsRequest.newBuilder().setScreeningId(99L).build(),
        observer);

    assertEquals(Status.Code.NOT_FOUND, observer.status().getStatus().getCode());
    assertTrue(observer.status().getStatus().getDescription().contains("99"));

    verifyNoInteractions(reservationRepository);
  }

  @Test
  void getReservedSeatsWithMissingScreeningIdReportsInvalidArgument() {
    CapturingObserver<ReservedSeatsResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.getReservedSeats(
        ReservedSeatsRequest.newBuilder().setScreeningId(0L).build(),
        observer);

    assertEquals(Status.Code.INVALID_ARGUMENT, observer.status().getStatus().getCode());

    verifyNoInteractions(screeningRepository, reservationRepository);
  }

  @Test
  void getReservedSeatsReportsInternalErrorWhenRepositoryFails() {
    when(screeningRepository.existsById(10L)).thenReturn(true);
    when(reservationRepository.findByScreening_IdOrderByIdAsc(10L))
        .thenThrow(new DataAccessResourceFailureException("database offline"));

    CapturingObserver<ReservedSeatsResponse> observer =
        new CapturingObserver<>();

    screeningGrpcService.getReservedSeats(
        ReservedSeatsRequest.newBuilder().setScreeningId(10L).build(),
        observer);

    StatusRuntimeException error = observer.status();
    assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
    assertEquals("The reserved seats could not be loaded.",
        error.getStatus().getDescription());
    assertFalse(error.getStatus().getDescription().contains("database offline"));
    assertFalse(observer.completed);
  }

  private static CreateReservationRequest reservationRequest(
      long screeningId,
      String seatNumber) {
    return CreateReservationRequest.newBuilder()
        .setScreeningId(screeningId)
        .setSeatNumber(seatNumber)
        .build();
  }

  private static ScreeningEntity screening(long id) {
    return entity(id, "Dune: Part Two",
        LocalDateTime.of(2026, 12, 5, 21, 0), "Sal 2");
  }

  private static ReservationEntity reservation(
      long id,
      ScreeningEntity screening,
      String seatNumber) {
    ReservationEntity entity = new ReservationEntity(screening, seatNumber);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static CreateScreeningRequest request(
      String movieTitle,
      String startTime,
      String room) {
    return CreateScreeningRequest.newBuilder()
        .setMovieTitle(movieTitle)
        .setStartTime(startTime)
        .setRoom(room)
        .build();
  }

  private static ScreeningEntity entity(
      long id,
      String movieTitle,
      LocalDateTime startTime,
      String room) {
    ScreeningEntity entity = new ScreeningEntity(movieTitle, startTime, room);
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  /** Minimal observer that records what the service publishes. */
  private static final class CapturingObserver<T>
      implements StreamObserver<T> {

    private final List<T> values = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      this.error = throwable;
    }

    @Override
    public void onCompleted() {
      this.completed = true;
    }

    private StatusRuntimeException status() {
      assertNotNull(error, "expected the call to fail");
      assertTrue(error instanceof StatusRuntimeException,
          "expected a StatusRuntimeException");
      return (StatusRuntimeException) error;
    }
  }
}
