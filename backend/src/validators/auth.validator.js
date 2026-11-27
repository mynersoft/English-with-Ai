import { AppError } from "@/lib/errors";

export async function readJson(request) {
  let raw;
  try {
    raw = await request.text();
  } catch {
    throw new AppError(400, "INVALID_BODY", "Could not read request body.");
  }
  if (!raw || !raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new AppError(400, "INVALID_JSON", "Request body must be a JSON object.");
    }
    return parsed;
  } catch (err) {
    if (err instanceof AppError) throw err;
    throw new AppError(400, "INVALID_JSON", "Invalid JSON request body.");
  }
}

export function validateRegister(body) {
  const name = typeof body?.name === "string" ? body.name.trim().replace(/\s+/g, " ") : "";
  if (name.length < 2 || name.length > 60) {
    throw new AppError(400, "VALIDATION_ERROR", "Name must be between 2 and 60 characters.");
  }
  return { name };
}
