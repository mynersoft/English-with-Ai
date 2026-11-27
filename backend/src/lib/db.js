
import mongoose from "mongoose";
import { AppError } from "@/lib/errors";

const cached =
  global._mongoose ||
  (global._mongoose = {
    conn: null,
    promise: null,
  });

function sanitizeMongoMessage(msg) {
  return String(msg || "Unknown database error")
    .replace(/mongodb(\+srv)?:\/\/[^@\s]+@/gi, "mongodb$1://***@")
    .slice(0, 200);
}

function attachConnectionListeners() {
  if (global._mongooseListenersAttached) return;

  global._mongooseListenersAttached = true;

  mongoose.connection.on("disconnected", () => {
    cached.conn = null;
    cached.promise = null;
  });

  mongoose.connection.on("error", (err) => {
    console.error(
      "MongoDB runtime error:",
      sanitizeMongoMessage(err?.message)
    );

    if (mongoose.connection.readyState !== 1) {
      cached.conn = null;
      cached.promise = null;
    }
  });
}

export async function connectDB() {
  const uri = process.env.MONGODB_URI
    ?.trim()
    ?.replace(/^["']|["']$/g, "");

  if (!uri) {
    throw new AppError(
      500,
      "DB_NOT_CONFIGURED",
      "Database connection is not configured."
    );
  }

  if (
    cached.conn &&
    mongoose.connection.readyState === 1
  ) {
    return cached.conn;
  }

  attachConnectionListeners();

  if (!cached.promise) {
    cached.promise = mongoose.connect(uri, {
      bufferCommands: false,

      maxPoolSize: 10,
      minPoolSize: 0,

      maxIdleTimeMS: 60000,

      serverSelectionTimeoutMS: 6000,
      connectTimeoutMS: 6000,
    });
  }

  try {
    cached.conn = await cached.promise;
    return cached.conn;
  } catch (err) {
    cached.promise = null;
    cached.conn = null;

    console.error(
      "MongoDB connection error:",
      sanitizeMongoMessage(err?.message)
    );

    throw new AppError(
      503,
      "DB_UNAVAILABLE",
      "Database service is temporarily unavailable. Please try again shortly.",
      { "Retry-After": "3" }
    );
  }
}
