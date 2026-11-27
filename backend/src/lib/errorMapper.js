const TRANSIENT_MONGO_NAMES = new Set([
  "MongoNetworkError",
  "MongoServerSelectionError",
  "MongoTimeoutError",
  "MongoNotConnectedError",
  "MongoWaitQueueTimeoutError",
  "MongooseServerSelectionError",
]);

export function sanitizeServerText(msg) {
  return String(msg || "")
    .replace(/mongodb(\+srv)?:\/\/[^@\s]+@/gi, "mongodb$1://***@")
    .slice(0, 200);
}

function buildDuplicateKeyMessage(err) {
  const keys = Object.keys(err?.keyPattern || err?.keyValue || {});
  if (keys.includes("email")) return "An account with this email already exists.";
  if (keys.includes("slug")) return "An item with this name or slug already exists.";
  if (keys.includes("firebaseUid")) return "This account is already registered.";
  if (keys.length > 0) return `A record with this ${keys[0]} already exists.`;
  return "Duplicate record already exists.";
}

/** Maps any thrown error into a safe, consistent { status, code, message, headers } descriptor. */
export function normalizeApiError(
  err,
  fallbackMessage = "Something went wrong on the server. Please try again."
) {
  if (err && typeof err.status === "number" && typeof err.code === "string") {
    return {
      status: err.status || 500,
      code: err.code || "APP_ERROR",
      message: err.message || fallbackMessage,
      headers: err.headers || undefined,
    };
  }

  if (err?.code === 11000) {
    return {
      status: 409,
      code: "DUPLICATE",
      message: buildDuplicateKeyMessage(err),
    };
  }

  if (err?.name === "ValidationError") {
    const firstErr = err?.errors ? Object.values(err.errors)[0] : null;
    return {
      status: 400,
      code: "VALIDATION_ERROR",
      message: firstErr?.message || "Invalid input values provided.",
    };
  }

  if (err?.name === "CastError") {
    const field = err?.path && err.path !== "_id" ? err.path : "identifier";
    return {
      status: 400,
      code: "INVALID_ID",
      message: `Invalid ${field}.`,
    };
  }

  if (err instanceof SyntaxError || err?.type === "entity.parse.failed") {
    return {
      status: 400,
      code: "INVALID_JSON",
      message: "Invalid JSON request body.",
    };
  }

  if (
    TRANSIENT_MONGO_NAMES.has(err?.name) ||
    /timed?\s*out|econnreset|etimedout/i.test(String(err?.message || ""))
  ) {
    return {
      status: 503,
      code: "SERVICE_UNAVAILABLE",
      message: "Database service is temporarily busy. Please try again shortly.",
      headers: { "Retry-After": "3" },
    };
  }

  return {
    status: 500,
    code: "INTERNAL_ERROR",
    message: fallbackMessage,
  };
}
