import { NextResponse } from "next/server";

// One response format everywhere: { success, data, error }
export function ok(data = null, status = 200) {
  return NextResponse.json({ success: true, data, error: null }, { status });
}

export function fail(status, code, message, headers = undefined) {
  return NextResponse.json(
    { success: false, data: null, error: { code, message } },
    headers ? { status, headers } : { status }
  );
}
