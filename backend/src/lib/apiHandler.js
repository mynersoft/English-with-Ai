import crypto from "crypto";
import { AppError } from "@/lib/errors";
import { fail } from "@/lib/response";
import { clientIp, rateLimit } from "@/lib/rateLimit";
import { normalizeApiError, sanitizeServerText } from "./errorMapper";

export { normalizeApiError };

/** Wraps a route handler: rate limiting + consistent structured error responses. */
export function apiHandler(handler, { limit = 30 } = {}) {
  return async (request, context) => {
    try {
      const key = `${new URL(request.url).pathname}:${clientIp(request)}`;
      if (!rateLimit(key, { limit })) {
        throw new AppError(
          429,
          "RATE_LIMITED",
          "Too many requests. Please try again shortly.",
          { "Retry-After": "15" }
        );
      }
      return await handler(request, context);
    } catch (err) {
      const mapped = normalizeApiError(err);
      if (mapped.status >= 500) {
        const errorId = crypto.randomBytes(4).toString("hex");
        console.error(
          `Unhandled API error [${errorId}] (${err?.name || "Error"}):`,
          sanitizeServerText(err?.message),
          err?.stack || ""
        );
      }
      return fail(mapped.status, mapped.code, mapped.message, mapped.headers);
    }
  };
}
