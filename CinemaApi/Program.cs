using Cinema.Grpc;
using CinemaApi.Services;
using Grpc.Net.Client;

AppContext.SetSwitch(
    "System.Net.Http.SocketsHttpHandler.Http2UnencryptedSupport",
    true);

var builder = WebApplication.CreateBuilder(args);

string grpcServerUrl = builder.Configuration["Grpc:ServerUrl"] is { Length: > 0 } configuredUrl
    ? configuredUrl
    : "http://localhost:9090";

builder.Services.AddControllers();

// One channel and one generated client for the whole application lifetime.
builder.Services.AddSingleton(_ => GrpcChannel.ForAddress(grpcServerUrl));
builder.Services.AddSingleton(sp =>
    new ScreeningService.ScreeningServiceClient(
        sp.GetRequiredService<GrpcChannel>()));
builder.Services.AddScoped<IScreeningService, ScreeningGrpcService>();

var app = builder.Build();

app.Logger.LogInformation(
    "Forwarding screening calls to the gRPC server at {GrpcServerUrl}",
    grpcServerUrl);

app.MapGet("/", () => "Cinema API is running.");

app.MapControllers();

app.Run();
