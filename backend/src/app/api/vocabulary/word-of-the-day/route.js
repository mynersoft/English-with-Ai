import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import {
  getUserVocabSettings,
  getWordOfTheDay,
} from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(
  async (_request, { decoded, userDoc }) => {
    const settings = await getUserVocabSettings(userDoc);
    const data = await getWordOfTheDay({
      userId: decoded.uid,
      settings,
    });
    return ok(data);
  },
  { limit: 60 }
);
