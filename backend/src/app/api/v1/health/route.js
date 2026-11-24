import { NextResponse } from "next/server";
import { connectDB } from "@/lib/db";
import { adminAuth } from "@/lib/firebaseAdmin";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/v1/health -> verifies Firebase Admin & MongoDB connectivity without exposing secrets.
export async function GET() {
  const status = {
    ok: true,
    firebaseAdmin: "ready",
    mongodb: "connected",
  };

  try {
    adminAuth();
  } catch (err) {
    status.ok = false;
    status.firebaseAdmin = err?.message || "misconfigured";
  }

  try {
    await connectDB();
  } catch (err) {
    status.ok = false;
    status.mongodb = err?.message || "connection failed";
  }

  return NextResponse.json(status, { status: status.ok ? 200 : 500 });
}
