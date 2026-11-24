import mongoose from "mongoose";
import { AppError } from "@/lib/errors";

// Reuse one connection across hot reloads and warm serverless invocations.
const cached = global._mongoose || (global._mongoose = { conn: null, promise: null });

function sanitizeMongoMessage(msg) {
  return String(msg || "Unknown database error")
    .replace(/mongodb(\+srv)?:\/\/[^@\s]+@/gi, "mongodb$1://***@")
    .slice(0, 200);
}

export async function connectDB() {
  const uri = process.env.MONGODB_URI?.trim()?.replace(/^["']|["']$/g, "");
  if (!uri) {
    throw new AppError(500, "DB_NOT_CONFIGURED", "MONGODB_URI is not configured on the server.");
  }
  if (cached.conn && mongoose.connection.readyState === 1) return cached.conn;
  if (!cached.promise) {
    cached.promise = mongoose.connect(uri, {
      bufferCommands: false,
      maxPoolSize: 10,
      minPoolSize: 1,
      maxIdleTimeMS: 60_000,
      serverSelectionTimeoutMS: 8000,
      connectTimeoutMS: 8000,
      socketTimeoutMS: 20_000,
    });
  }
  try {
    cached.conn = await cached.promise;
  } catch (err) {
    cached.promise = null;
    console.error("MongoDB connection error:", err?.message);
    throw new AppError(
      500,
      "DB_CONNECTION_FAILED",
      `MongoDB connection failed: ${sanitizeMongoMessage(err?.message)}`
    );
  }
  return cached.conn;
}
