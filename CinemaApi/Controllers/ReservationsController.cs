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
public class ReservationsController : ControllerBase
{
    private readonly IReservationService reservationService;
    private readonly ILogger<ReservationsController> logger;

    public ReservationsController(IReservationService reservationService, ILogger<ReservationsController> logger) {
        this.reservationService = reservationService;
        this.logger = logger;
    }
    
    [HttpGet("{screeningId}/seats")]
    public async Task<IActionResult> GetReservations(long screeningId, CancellationToken cancellationToken)
    {
        if (screeningId <= 0)
        {
            return GrpcErrorHandler.Error(
                StatusCodes.Status400BadRequest,
                "screeningId must be greater than 0.");
        }

        try
        {
            var response = await reservationService.GetReservedSeatsAsync(
                new ReservedSeatsRequest
                {
                    ScreeningId = screeningId
                },
                cancellationToken);

            var reservations = response.Reservations.Select(reservation => new
            {
                reservation.Id,
                reservation.ScreeningId,
                reservation.SeatNumber
            });

            return Ok(reservations);
        }
        catch (RpcException exception)
        {
            return GrpcErrorHandler.GrpcFailure(exception, logger, "getting reserved seats");
        }
    }

    [HttpPost]
    public async Task<IActionResult> CreateReservation(
        [FromBody] CreateReservationRequestDto request,
        CancellationToken cancellationToken)
    {
        var errors = CreateReservationRequestValidator.Validate(request);

        if (errors.Count > 0)
        {
            return GrpcErrorHandler.Error(
                StatusCodes.Status400BadRequest,
                string.Join(" ", errors));
        }

        try
        {
            var response = await reservationService.CreateReservationAsync(
                new CreateReservationRequest
                {
                    ScreeningId = request.ScreeningId,
                    SeatNumber = request.SeatNumber
                },
                cancellationToken);

            if (!response.Success)
            {
                return GrpcErrorHandler.Error(
                    StatusCodes.Status400BadRequest,
                    string.IsNullOrWhiteSpace(response.Message)
                        ? "The reservation could not be created."
                        : response.Message);
            }

            return Ok(new
            {
                response.Reservation.Id,
                response.Reservation.ScreeningId,
                response.Reservation.SeatNumber
            });
        }
        catch (RpcException exception)
        {
            return GrpcErrorHandler.GrpcFailure(exception, logger, "creating a reservation");
        }
    }
}