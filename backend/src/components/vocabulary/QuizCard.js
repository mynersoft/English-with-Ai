"use client";

import { useEffect, useState } from "react";
import { apiFetch, playWordAudio } from "@/components/vocabulary/VocabClient";

const QUIZ_MODES = [
  { id: "en_to_bn", label: "English → Bengali MCQ" },
  { id: "bn_to_en", label: "Bengali → English MCQ" },
  { id: "fill_blank", label: "Fill in the Blanks" },
  { id: "word_match", label: "Word Matching" },
  { id: "listening", label: "Listening Quiz" },
  { id: "sentence_building", label: "Sentence Building" },
];

export default function QuizCard({ defaultLevel = "" }) {
  const [quizType, setQuizType] = useState("en_to_bn");
  const [quizData, setQuizData] = useState(null);
  const [answers, setAnswers] = useState({});
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [startTime, setStartTime] = useState(Date.now());

  // Sentence building state
  const [dailyWords, setDailyWords] = useState([]);
  const [selectedWordId, setSelectedWordId] = useState("");
  const [sentenceInput, setSentenceInput] = useState("");
  const [sentenceFeedback, setSentenceFeedback] = useState(null);

  async function loadQuiz(mode = quizType) {
    setError("");
    setResult(null);
    setAnswers({});
    setSentenceFeedback(null);
    setLoading(true);
    try {
      if (mode === "sentence_building") {
        const daily = await apiFetch("/api/vocabulary/daily");
        let pool = daily.words || [];
        if (pool.length === 0) {
          const browse = await apiFetch("/api/vocabulary/words?limit=10");
          pool = browse.words || [];
        }
        setDailyWords(pool);
        setSelectedWordId(pool[0]?.id || "");
        setSentenceInput("");
      } else {
        const qs = new URLSearchParams({ type: mode, count: "5" });
        if (defaultLevel) qs.set("level", defaultLevel);
        const data = await apiFetch(`/api/vocabulary/quiz?${qs.toString()}`);
        setQuizData(data);
        setStartTime(Date.now());
      }
    } catch (e) {
      setError(e.message || "Failed to load quiz.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadQuiz(quizType);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quizType]);

  async function submitQuiz(e) {
    e.preventDefault();
    if (!quizData) return;
    setLoading(true);
    setError("");
    try {
      const elapsedSec = Math.max(1, Math.round((Date.now() - startTime) / 1000));
      const payloadAnswers =
        quizData.quizType === "word_match"
          ? (quizData.leftItems || []).map((item) => ({
              vocabularyId: item.vocabularyId,
              matchedMeaning: answers[item.vocabularyId] || "",
            }))
          : (quizData.questions || []).map((q) => ({
              vocabularyId: q.vocabularyId,
              selectedAnswer: answers[q.vocabularyId] || "",
            }));

      const res = await apiFetch("/api/vocabulary/quiz", {
        method: "POST",
        body: {
          quizType: quizData.quizType,
          answers: payloadAnswers,
          timeTaken: elapsedSec,
        },
      });
      setResult(res);
    } catch (e) {
      setError(e.message || "Failed to submit quiz.");
    } finally {
      setLoading(false);
    }
  }

  async function submitSentence(e) {
    e.preventDefault();
    if (!selectedWordId || !sentenceInput.trim()) return;
    setLoading(true);
    setError("");
    try {
      const activeWord = dailyWords.find((w) => w.id === selectedWordId);
      const res = await apiFetch("/api/vocabulary/sentence-check", {
        method: "POST",
        body: {
          vocabularyId: selectedWordId,
          sentence: sentenceInput.trim(),
          expectedEnglish: activeWord?.examples?.[0]?.en || "",
          bengaliPrompt: activeWord?.examples?.[0]?.bn || activeWord?.meaning || "",
        },
      });
      setSentenceFeedback(res);
    } catch (e) {
      setError(e.message || "Failed to evaluate sentence.");
    } finally {
      setLoading(false);
    }
  }

  const activeSentenceWord = dailyWords.find((w) => w.id === selectedWordId);

  return (
    <div style={{ display: "grid", gap: 18 }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {QUIZ_MODES.map((m) => (
          <button
            key={m.id}
            type="button"
            className={`v-btn ${quizType === m.id ? "v-btn-primary" : "v-btn-outline"}`}
            onClick={() => setQuizType(m.id)}
          >
            {m.label}
          </button>
        ))}
      </div>

      {error && <div className="v-alert v-alert-error">{error}</div>}

      {loading && <div className="v-card">Loading exercise...</div>}

      {!loading && quizType === "sentence_building" && (
        <div className="v-card" style={{ display: "grid", gap: 16 }}>
          <h2 style={{ margin: 0, fontSize: 20 }}>Sentence Building Exercise</h2>
          {dailyWords.length === 0 ? (
            <p style={{ color: "var(--v-text-muted)" }}>
              No published vocabulary words found yet. Ask an admin to publish words first.
            </p>
          ) : (
            <form onSubmit={submitSentence} style={{ display: "grid", gap: 14 }}>
              <label style={{ display: "grid", gap: 6, fontSize: 14, fontWeight: 600 }}>
                Target Word
                <select
                  className="v-select"
                  value={selectedWordId}
                  onChange={(e) => {
                    setSelectedWordId(e.target.value);
                    setSentenceFeedback(null);
                  }}
                >
                  {dailyWords.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.word} — {w.meaning}
                    </option>
                  ))}
                </select>
              </label>

              {activeSentenceWord && (
                <div className="v-alert v-alert-info" style={{ display: "grid", gap: 4 }}>
                  <div>
                    <strong>Bengali Prompt:</strong>{" "}
                    {activeSentenceWord.examples?.[0]?.bn || activeSentenceWord.meaning}
                  </div>
                  <div style={{ fontSize: 13 }}>
                    Write an English sentence using <strong>{activeSentenceWord.word}</strong>.
                  </div>
                </div>
              )}

              <textarea
                className="v-textarea"
                rows={3}
                value={sentenceInput}
                onChange={(e) => setSentenceInput(e.target.value)}
                placeholder="Type your English sentence here..."
                required
              />

              <button type="submit" className="v-btn v-btn-primary" disabled={loading}>
                Check Sentence
              </button>
            </form>
          )}

          {sentenceFeedback && (
            <div style={{ display: "grid", gap: 10 }}>
              {!sentenceFeedback.aiConfigured && (
                <div className="v-alert v-alert-warning">{sentenceFeedback.aiStatusMessage}</div>
              )}
              <div
                className={`v-alert ${
                  sentenceFeedback.evaluation?.isAcceptable ? "v-alert-success" : "v-alert-warning"
                }`}
              >
                <div style={{ fontWeight: 700, marginBottom: 6 }}>
                  {sentenceFeedback.evaluation?.isAcceptable ? "Great Attempt!" : "Needs Improvement"}
                </div>
                <div>{sentenceFeedback.evaluation?.feedbackBn}</div>
                {sentenceFeedback.evaluation?.correctedSentence && (
                  <div style={{ marginTop: 8, fontSize: 13 }}>
                    <strong>Reference:</strong> {sentenceFeedback.evaluation.correctedSentence}
                  </div>
                )}
                {sentenceFeedback.aiEvaluated && (
                  <div style={{ display: "flex", gap: 10, marginTop: 8, flexWrap: "wrap" }}>
                    <span className="v-pill">Grammar: {sentenceFeedback.evaluation.grammarScore}%</span>
                    <span className="v-pill">Vocabulary: {sentenceFeedback.evaluation.vocabularyUsageScore}%</span>
                    <span className="v-pill">Structure: {sentenceFeedback.evaluation.structureScore}%</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {!loading && quizType !== "sentence_building" && result && (
        <div className="v-card" style={{ display: "grid", gap: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 10 }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 24 }}>Quiz Score: {result.score}%</h2>
              <p style={{ margin: "4px 0 0", color: "var(--v-text-muted)" }}>
                {result.correctAnswers} correct, {result.wrongAnswers} wrong ({result.timeTaken}s)
              </p>
            </div>
            <button type="button" className="v-btn v-btn-primary" onClick={() => loadQuiz(quizType)}>
              Retry Quiz
            </button>
          </div>

          <div style={{ display: "grid", gap: 10 }}>
            {(result.results || []).map((r, idx) => (
              <div
                key={idx}
                className={`v-alert ${r.isCorrect ? "v-alert-success" : "v-alert-error"}`}
              >
                <strong>
                  {r.isCorrect ? "✓" : "✗"} {r.word}
                </strong>{" "}
                — Your answer: {r.userAnswer || "(blank)"} | Correct: {r.correctAnswer}
              </div>
            ))}
          </div>
        </div>
      )}

      {!loading && quizType !== "sentence_building" && !result && quizData && (
        <form onSubmit={submitQuiz} style={{ display: "grid", gap: 16 }}>
          {quizData.quizType === "word_match" ? (
            <div className="v-card" style={{ display: "grid", gap: 14 }}>
              <h2 style={{ margin: 0, fontSize: 18 }}>Match each English word to its Bengali meaning</h2>
              {(quizData.leftItems || []).map((item) => (
                <div
                  key={item.vocabularyId}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr 1fr",
                    gap: 12,
                    alignItems: "center",
                    padding: 10,
                    background: "var(--v-bg)",
                    borderRadius: 10,
                  }}
                >
                  <div>
                    <strong>{item.word}</strong>{" "}
                    <span style={{ color: "var(--v-text-muted)", fontSize: 13 }}>{item.pronunciation}</span>
                  </div>
                  <select
                    className="v-select"
                    value={answers[item.vocabularyId] || ""}
                    onChange={(e) =>
                      setAnswers((prev) => ({ ...prev, [item.vocabularyId]: e.target.value }))
                    }
                    required
                  >
                    <option value="">Select meaning...</option>
                    {(quizData.rightItems || []).map((opt) => (
                      <option key={opt.vocabularyId} value={opt.meaning}>
                        {opt.meaning}
                      </option>
                    ))}
                  </select>
                </div>
              ))}
            </div>
          ) : (
            (quizData.questions || []).map((q, idx) => (
              <div key={q.questionId} className="v-card" style={{ display: "grid", gap: 12 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10 }}>
                  <div style={{ fontWeight: 700, fontSize: 17 }}>
                    Q{idx + 1}. {q.prompt}
                  </div>
                  {quizData.quizType === "listening" && (
                    <button
                      type="button"
                      className="v-btn v-btn-outline"
                      onClick={() => playWordAudio(q.speakHint, q.audioUrl)}
                    >
                      🔊 Play Audio
                    </button>
                  )}
                </div>

                {q.meaningHint && (
                  <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>Hint: {q.meaningHint}</div>
                )}

                <div className="v-grid v-grid-2">
                  {(q.options || []).map((opt, oIdx) => {
                    const checked = answers[q.vocabularyId] === opt.text;
                    return (
                      <label
                        key={oIdx}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 10,
                          padding: "12px 14px",
                          borderRadius: 12,
                          border: `2px solid ${checked ? "var(--v-primary)" : "var(--v-border)"}`,
                          background: checked ? "var(--v-primary-soft)" : "#fff",
                          cursor: "pointer",
                          fontWeight: 600,
                        }}
                      >
                        <input
                          type="radio"
                          name={q.vocabularyId}
                          value={opt.text}
                          checked={checked}
                          onChange={() =>
                            setAnswers((prev) => ({ ...prev, [q.vocabularyId]: opt.text }))
                          }
                          required
                        />
                        <span>{opt.text}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            ))
          )}

          <button type="submit" className="v-btn v-btn-primary" disabled={loading}>
            Submit Quiz Answers
          </button>
        </form>
      )}
    </div>
  );
}
