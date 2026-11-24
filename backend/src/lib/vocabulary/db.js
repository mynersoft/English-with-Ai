import mongoose from "mongoose";
import { connectDB } from "@/lib/db";

// Reuse the existing shared Mongoose connection without creating a duplicate pool.
export { connectDB };

/**
 * Runs a callback inside a MongoDB transaction when supported (replica set / Atlas),
 * and gracefully executes directly if the deployment is a standalone mongod.
 */
export async function runWithOptionalTransaction(work) {
  await connectDB();
  let session;
  try {
    session = await mongoose.startSession();
    let result;
    await session.withTransaction(async () => {
      result = await work(session);
    });
    return result;
  } catch (err) {
    const msg = String(err?.message || "");
    const code = err?.code;
    const unsupported =
      code === 20 ||
      msg.includes("Transaction numbers are only allowed on a replica set") ||
      msg.includes("does not support transactions");
    if (unsupported) {
      return await work(null);
    }
    throw err;
  } finally {
    if (session) {
      await session.endSession().catch(() => {});
    }
  }
}
