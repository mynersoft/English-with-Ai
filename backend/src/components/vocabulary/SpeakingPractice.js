"use client";

import { useEffect, useRef, useState } from "react";
import { apiFetch } from "@/components/vocabulary/VocabClient";

export default function SpeakingPractice() {
  const [targetWords, setTargetWords] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [aiConfigured, setAiConfigured] = useState(false);
  const [speechSupported, setSpeechSupported] = useState(false);
  const [listening, setListening] = useState(false);
  const [transcript, setTranscript] = useState("");
  const [seconds, setSeconds] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [latestResult, setLatestResult] = useState(null);
  const [error, setError] = useState("");
  const recognitionRef = useRef(null);
  const timerRef = useRef(null);

  useEffect(() => {
    const SpeechRec =
      typeof window !== "undefined" &&
      (window.SpeechRecognition || window.webkitSpeechRecognition);
    setSpeechSupported(Boolean(SpeechRec));

    async function load() {
      try {
        const [daily, spk] = await Promise.all([
          apiFetch("/api/vocabulary/daily"),
          apiFetch("/api/vocabulary/speaking"),
        ]);
        setTargetWords(daily.words || []);
        setSessions(spk.sessions || []);
        setAiConfigured(Boolean(spk.aiConfigured));
      } catch (e) {
        setError(e.message || "Failed to load speaking practice.");
      }
    }
    load();

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch {}
      }
    };
  }, []);

  function startRecording() {
    setError("");
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRec) {
      setError("Speech recognition is not supported in this browser. You can type your spoken response below.");
      return;
    }
    const rec = new SpeechRec();
    rec.lang = "en-US";
    rec.continuous = true;
    rec.interimResults = true;
    rec.onresult = (event) => {
      let combined = "";
      for (let i = 0; i < event.results.length; i += 1) {
        combined += `${event.results[i][0].transcript} `;
      }
      setTranscript(combined.trim());
    };
    rec.onerror = (ev) => {
      setError(`Microphone error: ${ev.error || "unavailable"}`);
      stopRecording();
    };
    rec.onend = () => {
      setListening(false);
      if (timerRef.current) clearInterval(timerRef.current);
    };
    recognitionRef.current = rec;
    setListening(true);
    setSeconds(0);
    timerRef.current = setInterval(() => setSeconds((s) => s + 1), 1000);
    rec.start();
  }

  function stopRecording() {
    setListening(false);
    if (timerRef.current) clearInterval(timerRef.current);
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {}
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!transcript.trim()) return;
    setSubmitting(true);
    setError("");
    try {
      const res = await apiFetch("/api/vocabulary/speaking", {
        method: "POST",
        body: {
          vocabularyIds: targetWords.map((w) => w.id),
          transcript: transcript.trim(),
          duration: seconds,
        },
      });
      setLatestResult(res);
      setSessions((prev) => [res.session, ...prev]);
      setTranscript("");
      setSeconds(0);
    } catch (e) {
      setError(e.message || "Failed to evaluate speaking session.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ display: "grid", gap: 18 }}>
      {!aiConfigured && (
        <div className="v-alert v-alert-warning">
          <strong>AI Evaluation Notice:</strong> Server <code>AI_API_KEY</code> is not configured.
          Speaking transcripts and target-word usage counts will still be saved to MongoDB, without simulated AI grammar/fluency scores.
        </div>
      )}

      {!speechSupported && (
        <div className="v-alert v-alert-info">
          Browser Web Speech Recognition is unavailable in this browser. Use a Chromium-based browser for microphone dictation, or type your practice sentences below.
        </div>
      )}

      {error && <div className="v-alert v-alert-error">{error}</div>}

      <div className="v-card" style={{ display: "grid", gap: 14 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>Today&apos;s 5 Target Vocabulary Words</h2>
        {targetWords.length === 0 ? (
          <p style={{ color: "var(--v-text-muted)", margin: 0 }}>
            No daily words assigned yet (ensure published vocabulary exists).
          </p>
        ) : (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {targetWords.map((w) => {
              const used = new RegExp(`\\b${w.word}`, "i").test(transcript);
              return (
                <span key={w.id} className={`v-pill ${used ? "v-pill-green" : "v-pill-blue"}`}>
                  {used ? "✓ " : ""}{w.word} ({w.meaning})
                </span>
              );
            })}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: "grid", gap: 12 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            {!listening ? (
              <button
                type="button"
                className="v-btn v-btn-primary"
                disabled={!speechSupported}
                onClick={startRecording}
              >
                🎙 Start Microphone Recording
              </button>
            ) : (
              <button type="button" className="v-btn v-btn-danger" onClick={stopRecording}>
                ⏹ Stop Recording ({seconds}s)
              </button>
            )}
            <span style={{ fontSize: 13, color: "var(--v-text-muted)" }}>
              Try speaking 2–4 natural sentences using today&apos;s words.
            </span>
          </div>

          <textarea
            className="v-textarea"
            rows={4}
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
            placeholder="Your speech-to-text transcript will appear here..."
            required
          />

          <button type="submit" className="v-btn v-btn-success" disabled={submitting || !transcript.trim()}>
            {submitting ? "Saving & Evaluating..." : "Submit Speaking Session"}
          </button>
        </form>
      </div>

      {latestResult?.session && (
        <div className="v-card" style={{ display: "grid", gap: 10 }}>
          <h3 style={{ margin: 0, fontSize: 18 }}>Latest Session Result</h3>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            <span className="v-pill v-pill-blue">
              Used Words: {latestResult.session.usedWords.length}/{latestResult.session.targetWords.length}
            </span>
            {latestResult.session.grammarScore !== null && (
              <span className="v-pill v-pill-green">Grammar: {latestResult.session.grammarScore}%</span>
            )}
            {latestResult.session.vocabularyScore !== null && (
              <span className="v-pill v-pill-green">Vocabulary: {latestResult.session.vocabularyScore}%</span>
            )}
            {latestResult.session.fluencyScore !== null && (
              <span className="v-pill v-pill-green">Fluency: {latestResult.session.fluencyScore}%</span>
            )}
          </div>
          <p style={{ margin: 0, lineHeight: 1.6 }}>{latestResult.session.feedback}</p>
        </div>
      )}

      <div className="v-card" style={{ display: "grid", gap: 12 }}>
        <h3 style={{ margin: 0, fontSize: 18 }}>Speaking Session History</h3>
        {sessions.length === 0 ? (
          <p style={{ color: "var(--v-text-muted)", margin: 0 }}>No speaking sessions recorded yet.</p>
        ) : (
          sessions.map((s) => (
            <div
              key={s.id}
              style={{
                padding: 12,
                borderRadius: 12,
                background: "var(--v-bg)",
                border: "1px solid var(--v-border)",
                display: "grid",
                gap: 6,
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--v-text-muted)" }}>
                <span>{new Date(s.createdAt).toLocaleString()}</span>
                <span>Used: {(s.usedWords || []).join(", ") || "None"}</span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 600 }}>&ldquo;{s.transcript}&rdquo;</div>
              <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>{s.feedback}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
