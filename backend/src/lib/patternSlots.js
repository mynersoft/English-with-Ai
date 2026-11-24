// ---------- Audio "slots" of a pattern ----------
// Every piece of text that can have audio: title, meaning, each example (en + bn)
export function listSlots(p) {
  const slots = [
    { key: "title", text: p.title, lang: "en", holder: p, field: "titleAudio" },
    { key: "meaning", text: p.meaning, lang: "bn", holder: p, field: "meaningAudio" },
  ];
  (p.examples || []).forEach((ex) => {
    slots.push({ key: `ex_${ex._id}_en`, text: ex.en, lang: "en", holder: ex, field: "enAudio" });
    slots.push({ key: `ex_${ex._id}_bn`, text: ex.bn, lang: "bn", holder: ex, field: "bnAudio" });
  });
  return slots;
}

// body: { target: "all" | "title" | "meaning" | "example", index, lang }
export function pickSlots(p, { target, index, lang }) {
  const all = listSlots(p);
  if (target === "all") return all;
  if (target === "title") return all.filter((s) => s.key === "title");
  if (target === "meaning") return all.filter((s) => s.key === "meaning");
  if (target === "example") {
    const ex = (p.examples || [])[index];
    if (!ex) return [];
    return all.filter((s) => s.key === `ex_${ex._id}_${lang}`);
  }
  return [];
}

export const publicIdFor = (patternId, key) => `patterns/${patternId}/${key}`;
