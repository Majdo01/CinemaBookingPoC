package dk.via.cinema.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GrpcServerConfig {

  private static final Logger log =
      LoggerFactory.getLogger(GrpcServerConfig.class);

  private final int port;

  public GrpcServerConfig(@Value("${grpc.server.port:9090}") int port) {
    this.port = port;
  }

  @Bean(destroyMethod = "shutdown")
  public Server grpcServer(
      ScreeningGrpcService screeningGrpcService
  ) throws IOException {

    Server server = ServerBuilder
        .forPort(port)
        .addService(screeningGrpcService)
        .build();

    server.start();

    log.info("gRPC server started on port {}", port);

    return server;
  }
}
