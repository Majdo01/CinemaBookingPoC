using System.Text.Json;
using Cinema.Grpc;
using CinemaApi.Controllers;
using CinemaApi.DTOs;
using Grpc.Core;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging.Abstractions;

namespace CinemaApi.Tests;

public class ScreeningsControllerTests
{
    private static readonly DateTime ValidStartTime =
        new(2026, 12, 5, 21, 0, 0, DateTimeKind.Unspecified);

    private static readonly JsonSerializerOptions CamelCaseJson =
        new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };

    private static ScreeningsController CreateController(FakeScreeningService service) =>
        new(service, NullLogger<ScreeningsController>.Instance);

    private static CreateScreeningRequestDto ValidRequest() =>
        new("Perfect Days", "2026-12-05T21:00:00", "Sal 2");

    private static RpcException RpcError(StatusCode statusCode, string detail) =>
        new(new Status(statusCode, detail));

    private static JsonElement Serialize(object? value) =>
        JsonDocument.Parse(JsonSerializer.Serialize(value, CamelCaseJson)).RootElement;

    private static FakeScreeningService SuccessfulCreateService() =>
        new()
        {
            CreateHandler = (request, _) => Task.FromResult(new CreateScreeningResponse
            {
                Success = true,
                Message = "Screening created successfully.",
                Screening = new Screening
                {
                    Id = 42,
                    MovieTitle = request.MovieTitle,
                    StartTime = request.StartTime,
                    Room = request.Room
                }
            })
        };

    [Fact]
    public async Task CreateScreening_ValidRequest_Returns201WithCreatedScreening()
    {
        var service = SuccessfulCreateService();
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            ValidRequest(),
            CancellationToken.None);

        var created = Assert.IsType<CreatedResult>(result);
        Assert.Equal(201, created.StatusCode);
        Assert.Equal("/api/screenings", created.Location);

        JsonElement body = Serialize(created.Value);
        Assert.Equal(42, body.GetProperty("id").GetInt64());
        Assert.Equal("Perfect Days", body.GetProperty("movieTitle").GetString());
        Assert.Equal("2026-12-05T21:00:00", body.GetProperty("startTime").GetString());
        Assert.Equal("Sal 2", body.GetProperty("room").GetString());

        Assert.Equal(1, service.CreateCalls);
        Assert.Equal(
            new DateTime(2026, 12, 5, 21, 0, 0),
            DateTime.Parse(service.LastCreateRequest!.StartTime));
        Assert.Equal(ValidStartTime, DateTime.Parse(service.LastCreateRequest.StartTime));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public async Task CreateScreening_EmptyMovieTitle_Returns400(string? movieTitle)
    {
        var service = SuccessfulCreateService();
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            new CreateScreeningRequestDto(movieTitle!, "2026-12-05T21:00:00", "Sal 2"),
            CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);
        Assert.Contains(
            "movieTitle",
            Serialize(badRequest.Value).GetProperty("error").GetString());
        Assert.Equal(0, service.CreateCalls);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    public async Task CreateScreening_EmptyRoom_Returns400(string? room)
    {
        var service = SuccessfulCreateService();
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            new CreateScreeningRequestDto("Perfect Days", "2026-12-05T21:00:00", room!),
            CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);
        Assert.Contains(
            "room",
            Serialize(badRequest.Value).GetProperty("error").GetString());
        Assert.Equal(0, service.CreateCalls);
    }

    [Fact]
    public async Task CreateScreening_MissingRequiredFields_Returns400()
    {
        var service = SuccessfulCreateService();
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            new CreateScreeningRequestDto(null!, null!, null!),
            CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);

        string error = Serialize(badRequest.Value).GetProperty("error").GetString()!;
        Assert.Contains("movieTitle", error);
        Assert.Contains("startTime", error);
        Assert.Contains("room", error);
        Assert.Equal(0, service.CreateCalls);
    }

    [Theory]
    [InlineData("tomorrow evening")]
    [InlineData("2026-13-45T99:00:00")]
    [InlineData("2026-12-05 21:00:00")]
    [InlineData("2026-12-05T21:00:00Z")]
    [InlineData("")]
    public async Task CreateScreening_MalformedStartTime_Returns400(string startTime)
    {
        var service = SuccessfulCreateService();
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            new CreateScreeningRequestDto("Perfect Days", startTime, "Sal 2"),
            CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);
        Assert.Contains(
            "startTime",
            Serialize(badRequest.Value).GetProperty("error").GetString());
        Assert.Equal(0, service.CreateCalls);
    }

    [Fact]
    public async Task CreateScreening_GrpcInvalidArgument_Returns400WithServerMessage()
    {
        var service = new FakeScreeningService
        {
            CreateHandler = (_, _) => throw RpcError(
                StatusCode.InvalidArgument,
                "startTime must be a valid ISO-8601 local date-time.")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            ValidRequest(),
            CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);
        Assert.Equal(
            "startTime must be a valid ISO-8601 local date-time.",
            Serialize(badRequest.Value).GetProperty("error").GetString());
    }

    [Fact]
    public async Task CreateScreening_GrpcUnavailable_Returns503()
    {
        var service = new FakeScreeningService
        {
            CreateHandler = (_, _) => throw RpcError(
                StatusCode.Unavailable,
                "Error connecting to subchannel.")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            ValidRequest(),
            CancellationToken.None);

        ObjectResult unavailable = Assert.IsType<ObjectResult>(result);
        Assert.Equal(503, unavailable.StatusCode);
        Assert.Contains(
            "unavailable",
            Serialize(unavailable.Value).GetProperty("error").GetString());
    }

    [Fact]
    public async Task CreateScreening_GrpcUnimplemented_Returns501()
    {
        var service = new FakeScreeningService
        {
            CreateHandler = (_, _) => throw RpcError(
                StatusCode.Unimplemented,
                "Method not implemented.")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            ValidRequest(),
            CancellationToken.None);

        ObjectResult notImplemented = Assert.IsType<ObjectResult>(result);
        Assert.Equal(501, notImplemented.StatusCode);
    }

    [Fact]
    public async Task CreateScreening_GrpcUnexpectedFailure_Returns502WithoutDetails()
    {
        var service = new FakeScreeningService
        {
            CreateHandler = (_, _) => throw RpcError(
                StatusCode.Internal,
                "java.lang.IllegalStateException: connection pool exhausted")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.CreateScreening(
            ValidRequest(),
            CancellationToken.None);

        ObjectResult badGateway = Assert.IsType<ObjectResult>(result);
        Assert.Equal(502, badGateway.StatusCode);

        string error = Serialize(badGateway.Value).GetProperty("error").GetString()!;
        Assert.DoesNotContain("IllegalStateException", error);
    }

    [Fact]
    public async Task GetScreenings_ReturnsAllScreenings()
    {
        var service = new FakeScreeningService
        {
            GetHandler = _ => Task.FromResult(new ScreeningList
            {
                Screenings =
                {
                    new Screening
                    {
                        Id = 1,
                        MovieTitle = "Dune: Part Two",
                        StartTime = "2026-01-10T18:00",
                        Room = "Sal 1"
                    },
                    new Screening
                    {
                        Id = 2,
                        MovieTitle = "Oppenheimer",
                        StartTime = "2026-01-10T21:00",
                        Room = "Sal 3"
                    }
                }
            })
        };
        var controller = CreateController(service);

        IActionResult result = await controller.GetScreenings(CancellationToken.None);

        var ok = Assert.IsType<OkObjectResult>(result);
        var body = Assert.IsAssignableFrom<IEnumerable<object>>(ok.Value).ToList();
        Assert.Equal(2, body.Count);

        JsonElement first = Serialize(body[0]);
        Assert.Equal(1, first.GetProperty("id").GetInt64());
        Assert.Equal("Dune: Part Two", first.GetProperty("movieTitle").GetString());
        Assert.Equal("Sal 1", first.GetProperty("room").GetString());
    }

    [Fact]
    public async Task GetScreenings_GrpcUnavailable_Returns503()
    {
        var service = new FakeScreeningService
        {
            GetHandler = _ => throw RpcError(
                StatusCode.Unavailable,
                "Error connecting to subchannel.")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.GetScreenings(CancellationToken.None);

        ObjectResult unavailable = Assert.IsType<ObjectResult>(result);
        Assert.Equal(503, unavailable.StatusCode);
        Assert.Equal(
            "The Cinema application server is unavailable. Please try again later.",
            Serialize(unavailable.Value).GetProperty("error").GetString());
    }

    [Fact]
    public async Task GetScreenings_GrpcUnexpectedFailure_Returns502()
    {
        var service = new FakeScreeningService
        {
            GetHandler = _ => throw RpcError(
                StatusCode.Internal,
                "org.hibernate.exception.SQLGrammarException: could not extract ResultSet")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.GetScreenings(CancellationToken.None);

        ObjectResult badGateway = Assert.IsType<ObjectResult>(result);
        Assert.Equal(502, badGateway.StatusCode);

        string error = Serialize(badGateway.Value).GetProperty("error").GetString()!;
        Assert.DoesNotContain("SQLGrammarException", error);
    }

    [Fact]
    public async Task GetScreenings_GrpcInvalidArgument_Returns400()
    {
        var service = new FakeScreeningService
        {
            GetHandler = _ => throw RpcError(
                StatusCode.InvalidArgument,
                "Empty request not accepted.")
        };
        var controller = CreateController(service);

        IActionResult result = await controller.GetScreenings(CancellationToken.None);

        ObjectResult badRequest = Assert.IsType<ObjectResult>(result);
        Assert.Equal(400, badRequest.StatusCode);
    }
}
