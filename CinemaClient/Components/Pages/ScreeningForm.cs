using System.ComponentModel.DataAnnotations;

namespace CinemaClient.Components.Pages;

public class ScreeningForm
{
    [Required(ErrorMessage = "Please choose a movie.")]
    public string? MovieTitle { get; set; }

    [Required(ErrorMessage = "Please choose a date.")]
    public DateTime? Date { get; set; }

    [Required(ErrorMessage = "Please choose a time.")]
    public string? Time { get; set; }

    [Required(ErrorMessage = "Please choose a room.")]
    public string? Room { get; set; }
}