using Cinema.Grpc;
using CinemaApi.DTOs;
using CinemaApi.Helpers;
using CinemaApi.Services;
using CinemaApi.Validation;
using Grpc.Core;
using Microsoft.AspNetCore.Mvc;

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
            return GrpcErrorHandler.GrpcFailure(exception, logger,"listing screenings");
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
            return GrpcErrorHandler.Error(
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
                return GrpcErrorHandler.Error(
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
            return GrpcErrorHandler.GrpcFailure(exception, logger, "creating a screening");
        }
    }
}
