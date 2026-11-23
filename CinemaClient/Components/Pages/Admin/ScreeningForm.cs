using System.ComponentModel.DataAnnotations;

namespace CinemaClient.Components.Pages.Admin;

public class ScreeningForm : IValidatableObject
{
    [Required(ErrorMessage = "Please choose a movie.")]
    public string? MovieTitle { get; set; }

    [Required(ErrorMessage = "Please choose a date.")]
    public DateTime? Date { get; set; }

    [Required(ErrorMessage = "Please choose a time.")]
    public string? Time { get; set; }

    [Required(ErrorMessage = "Please choose a room.")]
    public string? Room { get; set; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        if (Date is { } date && date.Date < DateTime.Today)
        {
            yield return new ValidationResult(
                "The screening date cannot be in the past.",
                new[] {nameof(Date)});
        }
    }
}