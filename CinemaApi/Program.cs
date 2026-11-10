using Cinema.Grpc;
using Grpc.Net.Client;

AppContext.SetSwitch(
    "System.Net.Http.SocketsHttpHandler.Http2UnencryptedSupport",
    true);

var builder = WebApplication.CreateBuilder(args);

var app = builder.Build();

app.MapGet("/", () => "Cinema API is running.");

app.MapGet("/api/screenings", async () =>
{
    using var channel = GrpcChannel.ForAddress(
        "http://localhost:9090");

    var client = new ScreeningService.ScreeningServiceClient(
        channel);

    var response = await client.ListScreeningsAsync(
        new EmptyRequest());

    var screenings = response.Screenings.Select(screening => new
    {
        screening.Id,
        screening.MovieTitle,
        screening.StartTime,
        screening.Room
    });

    return Results.Ok(screenings);
});

app.Run();