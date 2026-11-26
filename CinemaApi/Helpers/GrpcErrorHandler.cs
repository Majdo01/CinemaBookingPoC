using Grpc.Core;
using Microsoft.AspNetCore.Mvc;
using GrpcStatusCode = Grpc.Core.StatusCode;

namespace CinemaApi.Helpers;

/// <summary>
/// Translates a gRPC failure into the HTTP status codes the client
/// understands, without leaking server-side stack traces.
/// </summary>
public static class GrpcErrorHandler
{
    public static ObjectResult GrpcFailure(
        RpcException exception,
        ILogger logger,
        string operation)
    {
        int statusCode = exception.StatusCode switch
        {
            GrpcStatusCode.InvalidArgument => StatusCodes.Status400BadRequest,
            GrpcStatusCode.Unimplemented => StatusCodes.Status501NotImplemented,
            GrpcStatusCode.Unavailable => StatusCodes.Status503ServiceUnavailable,
            _ => StatusCodes.Status502BadGateway
        };

        string message = exception.StatusCode switch
        {
            GrpcStatusCode.InvalidArgument =>
                string.IsNullOrWhiteSpace(exception.Status.Detail)
                    ? "The request was rejected by the Cinema application server."
                    : exception.Status.Detail,

            GrpcStatusCode.Unimplemented =>
                "The Cinema application server does not support this operation.",

            GrpcStatusCode.Unavailable =>
                "The Cinema application server is unavailable. Please try again later.",

            _ => "The Cinema application server returned an unexpected error."
        };

        logger.LogWarning(
            exception,
            "gRPC call failed while {Operation}: {StatusCode} {Detail}",
            operation,
            exception.StatusCode,
            exception.Status.Detail);

        return new ObjectResult(new { error = message })
        {
            StatusCode = statusCode
        };
    }
    
    public static ObjectResult Error(int statusCode, string message) =>
        new(new { error = message }) { StatusCode = statusCode };
}