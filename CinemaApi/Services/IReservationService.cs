using Cinema.Grpc;

namespace CinemaApi.Services;

public interface IReservationService
{
    Task<CreateReservationResponse> CreateReservationAsync(
        CreateReservationRequest request,
        CancellationToken cancellationToken);
    
    Task<ReservedSeatsResponse> GetReservedSeatsAsync(
        ReservedSeatsRequest request,
        CancellationToken cancellationToken);
}