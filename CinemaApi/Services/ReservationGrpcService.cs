using Cinema.Grpc;

namespace CinemaApi.Services;

/// <summary>
/// Thin wrapper around the generated gRPC client.
/// </summary>
public class ReservationGrpcService : IReservationService
{
    private static readonly TimeSpan CallTimeout = TimeSpan.FromSeconds(10);

    private readonly ScreeningService.ScreeningServiceClient client;

    public ReservationGrpcService(ScreeningService.ScreeningServiceClient client)
    {
        this.client = client;
    }

    public async Task<CreateReservationResponse> CreateReservationAsync(
        CreateReservationRequest request,
        CancellationToken cancellationToken = default)
    {
        return await client
            .CreateReservationAsync(
                request,
                deadline: CallDeadline,
                cancellationToken: cancellationToken)
            .ResponseAsync;
    }

    public async Task<ReservedSeatsResponse> GetReservedSeatsAsync(
        ReservedSeatsRequest request,
        CancellationToken cancellationToken = default)
    {
        return await client
            .GetReservedSeatsAsync(
                request,
                deadline: CallDeadline,
                cancellationToken: cancellationToken)
            .ResponseAsync;
    }

    private static DateTime CallDeadline => DateTime.UtcNow.Add(CallTimeout);
}