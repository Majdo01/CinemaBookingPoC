package dk.via.cinema.service;

import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.grpc.*;
import dk.via.cinema.repository.ScreeningRepository;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ScreeningGrpcService
    extends ScreeningServiceGrpc.ScreeningServiceImplBase {

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
      ScreeningList.Builder result =
          ScreeningList.newBuilder();

      for (ScreeningEntity entity :
          screeningRepository.findAll()) {

        Screening screening = Screening.newBuilder()
            .setId(entity.getId())
            .setMovieTitle(entity.getMovieTitle())
            .setStartTime(entity.getStartTime().toString())
            .setRoom(entity.getRoom())
            .build();

        result.addScreenings(screening);
      }

      responseObserver.onNext(result.build());
      responseObserver.onCompleted();

    } catch (Exception exception) {
      responseObserver.onError(exception);
    }
  }

  @Override
  public void createScreening(
      CreateScreeningRequest request,
      StreamObserver<CreateScreeningResponse> responseObserver){
    try
    {
      LocalDateTime startTime = LocalDateTime.parse(request.getStartTime());

      ScreeningEntity entity = new ScreeningEntity(request.getMovieTitle(), startTime, request.getRoom());

      ScreeningEntity savedEntity = screeningRepository.save(entity);

      Screening screening = Screening.newBuilder()
          .setId(savedEntity.getId())
          .setMovieTitle(savedEntity.getMovieTitle())
          .setStartTime(savedEntity.getStartTime().toString())
          .setRoom(savedEntity.getRoom())
          .build();

      CreateScreeningResponse response = CreateScreeningResponse.newBuilder()
          .setSuccess(true)
          .setMessage("Screening created successfully.")
          .setScreening(screening)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception exception){
      responseObserver.onError(exception);
    }
  }

}
