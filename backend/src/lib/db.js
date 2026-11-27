import mongoose from "mongoose";
import { AppError } from "@/lib/errors";

// Reuse one connection across hot reloads and warm serverless invocations.
const cached = global._mongoose || (global._mongoose = { conn: null, promise: null });

function sanitizeMongoMessage(msg) {
  return String(msg || "Unknown database error")
    .replace(/mongodb(\+srv)?:\/\/[^@\s]+@/gi, "mongodb$1://***@")
    .slice(0, 200);
}

function resetCache() {
  cached.conn = null;
  cached.promise = null;
}

function attachConnectionListeners() {
  if (global._mongooseListenersAttached) return;
  global._mongooseListenersAttached = true;

  mongoose.connection.on("disconnected", resetCache);

  mongoose.connection.on("error", (err) => {
    console.error("MongoDB runtime error:", sanitizeMongoMessage(err?.message));
    if (mongoose.connection.readyState !== 1) resetCache();
  });
}

export async function connectDB() {
  const uri = process.env.MONGODB_URI?.trim()?.replace(/^["']|["']$/g, "");
  if (!uri) {
    throw new AppError(500, "DB_NOT_CONFIGURED", "Database connection is not configured on the server.");
  }

  const state = mongoose.connection.readyState; // 0 off, 1 on, 2 connecting, 3 disconnecting

  // Healthy connection: reuse it.
  if (cached.conn && state === 1) return cached.conn;

  // We had a connection but it is now dead: drop the stale promise so we reconnect.
  if (cached.conn && state !== 1 && state !== 2) resetCache();

  attachConnectionListeners();

  if (!cached.promise) {
    cached.promise = mongoose.connect(uri, {
      bufferCommands: false,
      maxPoolSize: 10, // keep low for serverless to avoid Atlas connection limits
      minPoolSize: 0, // idle sockets get frozen between invocations
      maxIdleTimeMS: 60_000,
      serverSelectionTimeoutMS: 6000,
      connectTimeoutMS: 6000,
      socketTimeoutMS: 45_000, // allow slow admin list/search queries
      heartbeatFrequencyMS: 10_000,
    });
  }

  try {
    cached.conn = await cached.promise;
  } catch (err) {
    resetCache();
    console.error("MongoDB connection error:", sanitizeMongoMessage(err?.message));
    throw new AppError(
      503,
      "DB_UNAVAILABLE",
      "Database service is temporarily unavailable. Please try again shortly.",
      { "Retry-After": "3" }
    );
  }

  return cached.conn;
}
