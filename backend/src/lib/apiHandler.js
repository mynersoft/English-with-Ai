import { AppError } from "@/lib/errors";
import { fail } from "@/lib/response";
import { clientIp, rateLimit } from "@/lib/rateLimit";

/** Wraps a route handler: rate limiting + consistent error response. */
export function apiHandler(handler, { limit = 30 } = {}) {
  return async (request, context) => {
    try {
      const key = `${new URL(request.url).pathname}:${clientIp(request)}`;
      if (!rateLimit(key, { limit })) {
        throw new AppError(429, "RATE_LIMITED", "Too many requests. Please try again shortly.");
      }
      return await handler(request, context);
    } catch (err) {
      if (err instanceof AppError) return fail(err.status, err.code, err.message);
      if (err?.code === 11000) return fail(409, "DUPLICATE", "An account with this email already exists.");
      console.error("Unhandled API error:", err);
      const safeDetail = String(err?.message || "Internal server error")
        .replace(/mongodb(\+srv)?:\/\/[^@\s]+@/gi, "mongodb$1://***@")
        .slice(0, 180);
      return fail(500, "INTERNAL_ERROR", `${err?.name || "ServerError"}: ${safeDetail}`);
    }
  };
}
