import { NextResponse } from "next/server";
import { isAdmin } from "@/lib/audio";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/v1/admin/me -> checks whether the signed-in caller has admin access
export async function GET(req) {
  const admin = await isAdmin(req);
  return NextResponse.json({ admin });
}
