using Cinema.Grpc;
using CinemaApi.DTOs;
using CinemaApi.Services;
using Grpc.Core;
using Microsoft.AspNetCore.Mvc;
namespace CinemaApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ScreeningsController : ControllerBase
{
    private readonly IScreeningService screeningService;

    public ScreeningsController(IScreeningService screeningService)
    {
        this.screeningService = screeningService;
    }

    [HttpGet]
    public async Task<IActionResult> GetScreenings()
    {
        var response = await screeningService.GetScreeningsAsync();

        var screenings = response.Screenings.Select(screening => new
        {
            screening.Id,
            screening.MovieTitle,
            screening.StartTime,
            screening.Room
        });

        return Ok(screenings);
    }

    
    [HttpPost]
    public async Task<IActionResult> CreateScreening(
        [FromBody] CreateScreeningRequestDto request)
    {
        if (string.IsNullOrWhiteSpace(request.MovieTitle)
            || string.IsNullOrWhiteSpace(request.StartTime)
            || string.IsNullOrWhiteSpace(request.Room))
        {
            return BadRequest(new
            {
                error = "movieTitle, startTime and room are required."
            });
        }

        try
        {
            var response = await screeningService.CreateScreeningAsync(
                new CreateScreeningRequest
                {
                    MovieTitle = request.MovieTitle,
                    StartTime = request.StartTime,
                    Room = request.Room
                });

            if (!response.Success)
            {
                return BadRequest(new
                {
                    error = response.Message
                });
            }

            return Created(
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
                Grpc.Core.StatusCode.Unimplemented => StatusCodes.Status501NotImplemented,
                Grpc.Core.StatusCode.Unavailable => StatusCodes.Status503ServiceUnavailable,
                _ => StatusCodes.Status502BadGateway
            };

            return Problem(
                title: "The Cinema application server gRPC call failed.",
                detail: $"{exception.StatusCode}: {exception.Status.Detail}",
                statusCode: statusCode);
        }
    }

}