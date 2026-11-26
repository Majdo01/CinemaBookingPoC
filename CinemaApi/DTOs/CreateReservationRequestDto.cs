namespace CinemaApi.DTOs;

public sealed record CreateReservationRequestDto(
    long ScreeningId,
    string SeatNumber);