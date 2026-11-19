using Cinema.Grpc;
using Grpc.Core;
using Grpc.Net.Client;

AppContext.SetSwitch(
    "System.Net.Http.SocketsHttpHandler.Http2UnencryptedSupport",
    true);

var builder = WebApplication.CreateBuilder(args);

var grpcServerUrl = builder.Configuration["Grpc:ServerUrl"]
    ?? "http://localhost:9090";

var app = builder.Build();

app.MapGet("/", () => "Cinema API is running.");

app.MapGet("/api/screenings", async () =>
{
    using var channel = GrpcChannel.ForAddress(
        grpcServerUrl);

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

app.MapPost("/api/screenings", async (CreateScreeningRequestDto request) =>
{
    if (string.IsNullOrWhiteSpace(request.MovieTitle)
        || string.IsNullOrWhiteSpace(request.StartTime)
        || string.IsNullOrWhiteSpace(request.Room))
    {
        return Results.BadRequest(new
        {
            error = "movieTitle, startTime and room are required."
        });
    }

    using var channel = GrpcChannel.ForAddress(
        grpcServerUrl);

    var client = new ScreeningService.ScreeningServiceClient(
        channel);

    try
    {
        var response = await client.CreateScreeningAsync(
            new CreateScreeningRequest
            {
                MovieTitle = request.MovieTitle,
                StartTime = request.StartTime,
                Room = request.Room
            });

        if (!response.Success)
        {
            return Results.BadRequest(new
            {
                error = response.Message
            });
        }

        return Results.Created(
            $"/api/screenings/{response.Screening.Id}",
            new
            {
                response.Screening.Id,
                response.Screening.MovieTitle,
                response.Screening.StartTime,
                response.Screening.Room
            });
    }
    catch (RpcException exception)
    {
        var statusCode = exception.StatusCode switch
        {
            StatusCode.Unimplemented => StatusCodes.Status501NotImplemented,
            StatusCode.Unavailable => StatusCodes.Status503ServiceUnavailable,
            _ => StatusCodes.Status502BadGateway
        };

        return Results.Problem(
            title: "The Cinema application server gRPC call failed.",
            detail: $"{exception.StatusCode}: {exception.Status.Detail}",
            statusCode: statusCode);
    }
});

app.Run();

public sealed record CreateScreeningRequestDto(
    string MovieTitle,
    string StartTime,
    string Room);
