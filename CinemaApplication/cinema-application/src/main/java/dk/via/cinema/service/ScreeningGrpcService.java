package dk.via.cinema.service;

import dk.via.cinema.entity.ScreeningEntity;
import dk.via.cinema.repository.ScreeningRepository;
import dk.via.cinema.grpc.EmptyRequest;
import dk.via.cinema.grpc.Screening;
import dk.via.cinema.grpc.ScreeningList;
import dk.via.cinema.grpc.ScreeningServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

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
}
