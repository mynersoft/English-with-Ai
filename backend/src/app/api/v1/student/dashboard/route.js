import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { buildStudentDashboardData } from "@/modules/student/student.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/v1/student/dashboard -> unified student profile, streaks, weekly activity & course report
export const GET = withAuth(
  async (_request, { user }) => {
    const data = await buildStudentDashboardData({ userDoc: user });
    return ok(data);
  },
  { limit: 60 }
);
