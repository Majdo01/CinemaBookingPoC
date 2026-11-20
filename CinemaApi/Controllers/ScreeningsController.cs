using Cinema.Grpc;
using CinemaApi.DTOs;
using CinemaApi.Services;
using CinemaApi.Validation;
using Grpc.Core;
using Microsoft.AspNetCore.Mvc;
using GrpcStatusCode = Grpc.Core.StatusCode;

namespace CinemaApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ScreeningsController : ControllerBase
{
    private readonly IScreeningService screeningService;
    private readonly ILogger<ScreeningsController> logger;

    public ScreeningsController(
        IScreeningService screeningService,
        ILogger<ScreeningsController> logger)
    {
        this.screeningService = screeningService;
        this.logger = logger;
    }

    [HttpGet]
    public async Task<IActionResult> GetScreenings(CancellationToken cancellationToken)
    {
        try
        {
            var response = await screeningService.GetScreeningsAsync(cancellationToken);

            var screenings = response.Screenings.Select(screening => new
            {
                screening.Id,
                screening.MovieTitle,
                screening.StartTime,
                screening.Room
            });

            return Ok(screenings);
        }
        catch (RpcException exception)
        {
            return GrpcFailure(exception, "listing screenings");
        }
    }

    [HttpPost]
    public async Task<IActionResult> CreateScreening(
        [FromBody] CreateScreeningRequestDto request,
        CancellationToken cancellationToken)
    {
        IReadOnlyList<string> validationErrors =
            CreateScreeningRequestValidator.Validate(request);

        if (validationErrors.Count > 0)
        {
            return Error(
                StatusCodes.Status400BadRequest,
                string.Join(" ", validationErrors));
        }

        try
        {
            var response = await screeningService.CreateScreeningAsync(
                new CreateScreeningRequest
                {
                    MovieTitle = request.MovieTitle,
                    StartTime = request.StartTime,
                    Room = request.Room
                },
                cancellationToken);

            if (!response.Success)
            {
                return Error(
                    StatusCodes.Status400BadRequest,
                    string.IsNullOrWhiteSpace(response.Message)
                        ? "The screening could not be created."
                        : response.Message);
            }

            // The gRPC contract has no GET-by-id call, so the collection
            // endpoint is the only resource that really exists here.
            return Created(
                "/api/screenings",
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
            return GrpcFailure(exception, "creating a screening");
        }
    }

    private ObjectResult Error(int statusCode, string message) =>
        new(new { error = message }) { StatusCode = statusCode };

    /// <summary>
    /// Translates a gRPC failure into the HTTP status codes the client
    /// understands, without leaking server-side stack traces.
    /// </summary>
    private ObjectResult GrpcFailure(RpcException exception, string operation)
    {
        int statusCode = exception.StatusCode switch
        {
            GrpcStatusCode.InvalidArgument => StatusCodes.Status400BadRequest,
            GrpcStatusCode.Unimplemented => StatusCodes.Status501NotImplemented,
            GrpcStatusCode.Unavailable => StatusCodes.Status503ServiceUnavailable,
            _ => StatusCodes.Status502BadGateway
        };

        string message = exception.StatusCode switch
        {
            GrpcStatusCode.InvalidArgument => string.IsNullOrWhiteSpace(exception.Status.Detail)
                ? "The screening request was rejected by the Cinema application server."
                : exception.Status.Detail,
            GrpcStatusCode.Unimplemented =>
                "The Cinema application server does not support this operation.",
            GrpcStatusCode.Unavailable =>
                "The Cinema application server is unavailable. Please try again later.",
            _ => "The Cinema application server returned an unexpected error."
        };

        logger.LogWarning(
            exception,
            "gRPC call failed while {Operation}: {StatusCode} {Detail}",
            operation,
            exception.StatusCode,
            exception.Status.Detail);

        return Error(statusCode, message);
    }
}
