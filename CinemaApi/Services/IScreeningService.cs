using Cinema.Grpc;

namespace CinemaApi.Services;

public interface IScreeningService
{
    Task<ScreeningList> GetScreeningsAsync();

    Task<CreateScreeningResponse> CreateScreeningAsync(
        CreateScreeningRequest request);
}