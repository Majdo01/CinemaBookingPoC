using Cinema.Grpc;

namespace CinemaApi.Services;

/// <summary>
/// Thin wrapper around the generated gRPC client. The channel and the client
/// are created once by the DI container (see Program.cs) and reused for every
/// request, so no connection is set up per call.
/// </summary>
public class ScreeningGrpcService : IScreeningService
{
    private static readonly TimeSpan CallTimeout = TimeSpan.FromSeconds(10);

    private readonly ScreeningService.ScreeningServiceClient client;

    public ScreeningGrpcService(ScreeningService.ScreeningServiceClient client)
    {
        this.client = client;
    }

    public async Task<ScreeningList> GetScreeningsAsync(
        CancellationToken cancellationToken = default)
    {
        return await client
            .ListScreeningsAsync(
                new EmptyRequest(),
                deadline: CallDeadline,
                cancellationToken: cancellationToken)
            .ResponseAsync;
    }

    public async Task<CreateScreeningResponse> CreateScreeningAsync(
        CreateScreeningRequest request,
        CancellationToken cancellationToken = default)
    {
        return await client
            .CreateScreeningAsync(
                request,
                deadline: CallDeadline,
                cancellationToken: cancellationToken)
            .ResponseAsync;
    }

    private static DateTime CallDeadline => DateTime.UtcNow.Add(CallTimeout);
}
