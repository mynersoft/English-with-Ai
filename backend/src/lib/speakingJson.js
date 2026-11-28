/**
 * Robust JSON extraction & recovery for AI speaking/correction responses.
 * Handles markdown fences, unescaped newlines inside strings, double-escaped \uXXXX
 * Bangla sequences, and responses truncated mid-JSON by model token limits.
 */

export function decodeUnicodeEscapes(text) {
  let str = String(text ?? "");
  // Strip any trailing incomplete unicode escape or backslash left by mid-token truncation
  str = str.replace(/\\u[0-9a-fA-F]{0,3}$/, "").replace(/\\$/, "");
  // Decode literal \uXXXX sequences (including surrogate pairs automatically via JS char codes)
  str = str.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
  str = str.replace(/\\n/g, " ").replace(/\\t/g, " ").replace(/\\"/g, '"').replace(/\\\\/g, "\\");
  return str.trim();
}

function stripTrailingIncompleteEscape(s) {
  return s.replace(/\\u[0-9a-fA-F]{0,3}$/, "").replace(/\\$/, "");
}

function repairTruncatedJson(candidate) {
  let out = "";
  let inString = false;
  let escaped = false;
  const stack = [];

  for (let i = 0; i < candidate.length; i++) {
    const ch = candidate[i];
    if (inString) {
      if (escaped) {
        out += ch;
        escaped = false;
        continue;
      }
      if (ch === "\\") {
        out += ch;
        escaped = true;
        continue;
      }
      if (ch === '"') {
        out += ch;
        inString = false;
        continue;
      }
      if (ch === "\n") {
        out += "\\n";
        continue;
      }
      if (ch === "\r") {
        continue;
      }
      out += ch;
    } else {
      if (ch === '"') {
        out += ch;
        inString = true;
      } else if (ch === "{") {
        out += ch;
        stack.push("}");
      } else if (ch === "[") {
        out += ch;
        stack.push("]");
      } else if (ch === "}" || ch === "]") {
        out += ch;
        if (stack.length > 0 && stack[stack.length - 1] === ch) {
          stack.pop();
        }
      } else {
        out += ch;
      }
    }
  }

  if (inString) {
    out = stripTrailingIncompleteEscape(out) + '"';
  }
  // Remove trailing dangling key/colon/comma if truncated right after a separator
  out = out.replace(/,\s*"[^"]*"\s*:\s*$/, "");
  out = out.replace(/,\s*$/, "");

  while (stack.length > 0) {
    out += stack.pop();
  }
  return out;
}

function extractJsonStringField(text, key) {
  const keyPattern = new RegExp(`"${key}"\\s*:\\s*"`, "i");
  const match = keyPattern.exec(text);
  if (!match) return "";
  let i = match.index + match[0].length;
  let raw = "";
  let escaped = false;
  while (i < text.length) {
    const ch = text[i];
    if (escaped) {
      raw += "\\" + ch;
      escaped = false;
      i++;
      continue;
    }
    if (ch === "\\") {
      escaped = true;
      i++;
      continue;
    }
    if (ch === '"') {
      break;
    }
    raw += ch;
    i++;
  }
  return decodeUnicodeEscapes(raw);
}

function extractJsonBooleanField(text, key) {
  const re = new RegExp(`"${key}"\\s*:\\s*(true|false)`, "i");
  const match = re.exec(text);
  if (!match) return null;
  return match[1].toLowerCase() === "true";
}

export function parseAiJsonObject(raw) {
  const cleaned = String(raw || "")
    .replace(/```json\s*/gi, "")
    .replace(/```/g, "")
    .trim();
  const start = cleaned.indexOf("{");
  if (start === -1) return null;

  const end = cleaned.lastIndexOf("}");
  if (end > start) {
    try {
      return JSON.parse(cleaned.slice(start, end + 1));
    } catch {
      // fall through to repair
    }
  }

  const sliceFromStart = cleaned.slice(start);
  try {
    const repaired = repairTruncatedJson(sliceFromStart);
    return JSON.parse(repaired);
  } catch {
    // fall through to field scanner
  }

  const reply = extractJsonStringField(sliceFromStart, "reply");
  const correctedText = extractJsonStringField(sliceFromStart, "correctedText");
  const corrected = extractJsonStringField(sliceFromStart, "corrected");
  const original = extractJsonStringField(sliceFromStart, "original");
  const explanationBn = extractJsonStringField(sliceFromStart, "explanationBn");
  const textField = extractJsonStringField(sliceFromStart, "text");
  const hasMistake = extractJsonBooleanField(sliceFromStart, "hasMistake");

  if (!reply && !correctedText && !corrected && !textField && hasMistake === null) {
    return null;
  }

  const result = {};
  if (reply) result.reply = reply;
  if (textField) result.text = textField;
  if (correctedText) result.correctedText = correctedText;
  if (hasMistake !== null && !correctedText) {
    result.correction = {
      hasMistake,
      original,
      corrected,
      explanationBn,
    };
  } else if (hasMistake !== null) {
    result.hasMistake = hasMistake;
    result.explanationBn = explanationBn;
  }
  return result;
}

export function normalizeSpeakingModelOutput({ raw, originalMessage, allowCorrection = true }) {
  const message = String(originalMessage || "");
  const parsed = parseAiJsonObject(raw);

  if (!parsed || typeof parsed.reply !== "string" || !parsed.reply.trim()) {
    const plainRaw = String(raw || "")
      .replace(/```json\s*/gi, "")
      .replace(/```/g, "")
      .trim();
    const looksLikeBrokenJson = plainRaw.startsWith("{") || plainRaw.includes('"reply"');
    return {
      reply: looksLikeBrokenJson || !plainRaw
        ? "Could you say that again? Let's continue our practice!"
        : decodeUnicodeEscapes(plainRaw),
      correction: { hasMistake: false, original: message, corrected: "", explanationBn: "" },
    };
  }

  const cleanReply = decodeUnicodeEscapes(parsed.reply);
  const c = parsed.correction && typeof parsed.correction === "object" ? parsed.correction : {};
  const corrected = typeof c.corrected === "string" ? decodeUnicodeEscapes(c.corrected) : "";
  const hasMistake =
    allowCorrection &&
    c.hasMistake === true &&
    corrected !== "" &&
    corrected.toLowerCase() !== message.trim().toLowerCase();

  const explanationBn =
    hasMistake && typeof c.explanationBn === "string"
      ? decodeUnicodeEscapes(c.explanationBn)
      : "";

  return {
    reply: cleanReply || "Could you say that again? Let's continue our practice!",
    correction: {
      hasMistake,
      original: message,
      corrected: hasMistake ? corrected : "",
      explanationBn,
    },
  };
}
