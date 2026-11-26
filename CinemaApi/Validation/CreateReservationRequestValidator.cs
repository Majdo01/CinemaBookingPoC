using CinemaApi.DTOs;
namespace CinemaApi.Validation;

/// <summary>
/// Validates Create Reservation requests before they are forwarded over gRPC.
/// </summary>
public static class CreateReservationRequestValidator
{
        public static IReadOnlyList<string> Validate(CreateReservationRequestDto? request)
        {
            var errors = new List<string>();

            if (request is null)
            {
                errors.Add("A request body is required.");
                return errors;
            }

            if (request.ScreeningId <= 0)
            {
                errors.Add("screeningId must be greater than 0.");
            }

            if (string.IsNullOrWhiteSpace(request.SeatNumber))
            {
                errors.Add("seatNumber must not be empty.");
            }

            return errors;
        }
}