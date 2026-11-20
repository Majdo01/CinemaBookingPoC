using System.Globalization;
using CinemaApi.DTOs;

namespace CinemaApi.Validation;

/// <summary>
/// Validates Create Screening requests before they are forwarded over gRPC.
/// Keeping this in one place means malformed input never reaches the Java
/// service and is reported back as HTTP 400 with a readable message.
/// </summary>
public static class CreateScreeningRequestValidator
{
    /// <summary>
    /// The date-time format agreed for the gRPC contract. It matches what the
    /// Blazor client sends (<c>DateTime.ToString("s")</c>) and what the Java
    /// handler parses with <c>LocalDateTime.parse</c> (no time zone offset).
    /// </summary>
    public const string StartTimeFormat = "yyyy-MM-ddTHH:mm:ss";

    private static readonly string[] StartTimeFormats =
    {
        "yyyy-MM-ddTHH:mm:ss",
        "yyyy-MM-ddTHH:mm"
    };

    public static IReadOnlyList<string> Validate(CreateScreeningRequestDto? request)
    {
        var errors = new List<string>();

        if (request is null)
        {
            errors.Add("A request body is required.");
            return errors;
        }

        if (string.IsNullOrWhiteSpace(request.MovieTitle))
        {
            errors.Add("movieTitle must not be empty.");
        }

        if (string.IsNullOrWhiteSpace(request.Room))
        {
            errors.Add("room must not be empty.");
        }

        if (string.IsNullOrWhiteSpace(request.StartTime))
        {
            errors.Add("startTime must not be empty.");
        }
        else if (!IsValidStartTime(request.StartTime))
        {
            errors.Add(
                $"startTime must be a valid date and time in the format {StartTimeFormat} " +
                "(for example 2026-12-05T21:00:00).");
        }

        return errors;
    }

    public static bool IsValidStartTime(string value) =>
        DateTime.TryParseExact(
            value,
            StartTimeFormats,
            CultureInfo.InvariantCulture,
            DateTimeStyles.None,
            out _);
}
