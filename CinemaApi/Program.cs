using CinemaApi.Services;

AppContext.SetSwitch(
    "System.Net.Http.SocketsHttpHandler.Http2UnencryptedSupport",
    true);

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddScoped<IScreeningService, ScreeningGrpcService>();

var app = builder.Build();

app.MapGet("/", () => "Cinema API is running.");

app.MapControllers();

app.Run();