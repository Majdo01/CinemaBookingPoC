package dk.via.cinema.service;

import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.grpc.CreateScreeningRequest;
import dk.via.cinema.grpc.CreateScreeningResponse;
import dk.via.cinema.grpc.EmptyRequest;
import dk.via.cinema.grpc.ScreeningList;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningGrpcServiceTest {

  @Mock
  private ScreeningRepository screeningRepository;

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
