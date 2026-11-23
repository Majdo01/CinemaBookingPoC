using System.Globalization;
using System.Text.Json.Serialization;

namespace CinemaClient.Models;
public sealed class ScreeningDto
{
    [JsonPropertyName("id")] public long Id { get; set; }

    [JsonPropertyName("movieTitle")] public string MovieTitle { get; set; } = string.Empty;

    [JsonPropertyName("startTime")] public string StartTime { get; set; } = string.Empty;

    [JsonPropertyName("room")] public string Room { get; set; } = string.Empty;

    public DateTime StartTimeValue => DateTime.Parse(StartTime);

    public string DisplayDate =>
        StartTimeValue == DateTime.MinValue ? StartTime : StartTimeValue.ToString("dd.MM.yyyy");

    public string DisplayTime =>
        StartTimeValue == DateTime.MinValue ? StartTime : StartTimeValue.ToString("HH:mm");

    public static string FormatDate(string value)
    {
        DateTime parsed = Parse(value);
        return parsed == DateTime.MinValue ? value : parsed.ToString("dd.MM.yyyy");
    }

    public static string FormatTime(string value)
    {
        DateTime parsed = Parse(value);
        return parsed == DateTime.MinValue ? value : parsed.ToString("HH:mm");
    }

    private static DateTime Parse(string value) =>
        DateTime.TryParse(value, CultureInfo.InvariantCulture, DateTimeStyles.None, out DateTime parsed)
            ? parsed
            : DateTime.MinValue;
}

