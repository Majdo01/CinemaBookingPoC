namespace CinemaApi.DTOs;

public sealed record CreateScreeningRequestDto(
    string MovieTitle,
    string StartTime,
    string Room);