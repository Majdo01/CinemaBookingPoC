using System.Text.Json.Serialization;

namespace CinemaClient.Models;

public sealed class ReservedSeatDto
{
    [JsonPropertyName("seatNumber")]
    public string SeatNumber { get; set; } = string.Empty;
}