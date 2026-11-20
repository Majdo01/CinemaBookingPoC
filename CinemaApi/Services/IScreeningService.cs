using Cinema.Grpc;

namespace CinemaApi.Services;

public interface IScreeningService
{
    Task<ScreeningList> GetScreeningsAsync(
        CancellationToken cancellationToken = default);

    Task<CreateScreeningResponse> CreateScreeningAsync(
        CreateScreeningRequest request,
        CancellationToken cancellationToken = default);
}
