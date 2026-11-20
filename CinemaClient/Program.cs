using CinemaClient.Components;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

string cinemaApiBaseUrl = builder.Configuration["CinemaApi:BaseUrl"] is { Length: > 0 } configuredUrl
    ? configuredUrl
    : "http://localhost:5287/";

builder.Services.AddHttpClient("CinemaApi", client =>
{
    client.BaseAddress = new Uri(cinemaApiBaseUrl);
});

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error", createScopeForErrors: true);
    app.UseHsts();
}

app.UseStatusCodePagesWithReExecute(
    "/not-found",
    createScopeForStatusCodePages: true);

app.UseHttpsRedirection();
app.UseAntiforgery();

app.MapStaticAssets();

app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();
