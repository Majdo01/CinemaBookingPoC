
package dk.via.cinema.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GrpcServerConfig {

  @Bean(destroyMethod = "shutdown")
  public Server grpcServer(
      ScreeningGrpcService screeningGrpcService
  ) throws IOException {

    Server server = ServerBuilder
        .forPort(9090)
        .addService(screeningGrpcService)
        .build();

    server.start();

    System.out.println(
        "gRPC server started on port 9090"
    );

    return server;
  }
}
