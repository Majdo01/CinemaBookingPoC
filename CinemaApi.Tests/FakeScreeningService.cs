using Cinema.Grpc;
using CinemaApi.Services;

namespace CinemaApi.Tests;

/// <summary>
/// Stand-in for the gRPC service: lets each test decide what the service
/// returns or throws, so no running Java server or database is required.
/// </summary>
internal sealed class FakeScreeningService : IScreeningService
{
    public Func<CancellationToken, Task<ScreeningList>>? GetHandler { get; set; }

    public Func<CreateScreeningRequest, CancellationToken, Task<CreateScreeningResponse>>?
        CreateHandler { get; set; }

    public int GetCalls { get; private set; }

    public int CreateCalls { get; private set; }

    public CreateScreeningRequest? LastCreateRequest { get; private set; }

    public Task<ScreeningList> GetScreeningsAsync(
        CancellationToken cancellationToken = default)
    {
        GetCalls++;

        if (GetHandler is null)
        {
            throw new InvalidOperationException("No GET handler was configured.");
        }

        return GetHandler(cancellationToken);
    }

    public Task<CreateScreeningResponse> CreateScreeningAsync(
        CreateScreeningRequest request,
        CancellationToken cancellationToken = default)
    {
        CreateCalls++;
        LastCreateRequest = request;

        if (CreateHandler is null)
        {
            throw new InvalidOperationException("No create handler was configured.");
        }

        return CreateHandler(request, cancellationToken);
    }
}
