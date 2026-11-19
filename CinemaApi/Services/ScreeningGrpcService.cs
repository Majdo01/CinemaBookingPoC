using Cinema.Grpc;
using Grpc.Net.Client;

namespace CinemaApi.Services;

public class ScreeningGrpcService : IScreeningService
{
    private readonly string grpcServerUrl = "http://localhost:9090";

    public async Task<ScreeningList> GetScreeningsAsync()
    {
        using var channel = GrpcChannel.ForAddress(grpcServerUrl);

        var client = new ScreeningService.ScreeningServiceClient(channel);

        return await client.ListScreeningsAsync(new EmptyRequest());
    }

    public async Task<CreateScreeningResponse> CreateScreeningAsync(
        CreateScreeningRequest request)
    {
        using var channel = GrpcChannel.ForAddress(grpcServerUrl);

        var client = new ScreeningService.ScreeningServiceClient(channel);

        return await client.CreateScreeningAsync(request);
    }
}