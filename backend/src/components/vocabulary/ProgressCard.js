"use client";

export default function ProgressCard({ analytics }) {
  if (!analytics) return null;
  const { summary, weeklyActivity = [], levelBreakdown = [], recentQuizzes = [] } = analytics;
  const maxDayVal = Math.max(
    5,
    ...weeklyActivity.map((d) => (d.newWordsCompleted || 0) + (d.reviewedWords || 0))
  );
  const maxLevelVal = Math.max(1, ...levelBreakdown.map((l) => l.count || 0));

  return (
    <div style={{ display: "grid", gap: 18 }}>
      <div className="v-grid v-grid-4">
        <div className="v-card">
          <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>Total Learned</div>
          <div style={{ fontSize: 28, fontWeight: 800 }}>{summary.totalLearned}</div>
        </div>
        <div className="v-card">
          <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>Mastered</div>
          <div style={{ fontSize: 28, fontWeight: 800, color: "var(--v-success)" }}>{summary.mastered}</div>
        </div>
        <div className="v-card">
          <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>Quiz Accuracy</div>
          <div style={{ fontSize: 28, fontWeight: 800, color: "var(--v-primary)" }}>{summary.quizAccuracy}%</div>
        </div>
        <div className="v-card">
          <div style={{ fontSize: 13, color: "var(--v-text-muted)" }}>Current Streak</div>
          <div style={{ fontSize: 28, fontWeight: 800, color: "var(--v-warning)" }}>{summary.streak} days</div>
        </div>
      </div>

      {/* Weekly Activity SVG Bar Chart */}
      <div className="v-card">
        <h3 style={{ margin: "0 0 14px", fontSize: 17 }}>Weekly Activity (New + Reviewed Words)</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 10, alignItems: "end", height: 170 }}>
          {weeklyActivity.map((d) => {
            const total = (d.newWordsCompleted || 0) + (d.reviewedWords || 0);
            const pct = Math.round((total / maxDayVal) * 100);
            return (
              <div key={d.date} style={{ display: "grid", gap: 6, textAlign: "center", alignItems: "end", height: "100%" }}>
                <div style={{ fontSize: 12, fontWeight: 700 }}>{total}</div>
                <div
                  style={{
                    height: 110,
                    display: "flex",
                    alignItems: "flex-end",
                    justifyContent: "center",
                    background: "var(--v-surface-muted)",
                    borderRadius: 8,
                    overflow: "hidden",
                  }}
                >
                  <div
                    style={{
                      width: "100%",
                      height: `${pct}%`,
                      background: "linear-gradient(180deg, #2563eb, #4f46e5)",
                      transition: "height 0.3s ease",
                    }}
                  />
                </div>
                <div style={{ fontSize: 11, color: "var(--v-text-muted)" }}>{d.date.slice(5)}</div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="v-grid v-grid-2">
        {/* CEFR Level Progress */}
        <div className="v-card" style={{ display: "grid", gap: 10 }}>
          <h3 style={{ margin: 0, fontSize: 17 }}>CEFR Level Progress</h3>
          {levelBreakdown.map((item) => (
            <div key={item.level} style={{ display: "grid", gap: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, fontWeight: 600 }}>
                <span>{item.level}</span>
                <span>{item.count} words</span>
              </div>
              <div className="v-progress-track">
                <div
                  className="v-progress-fill"
                  style={{ width: `${Math.round(((item.count || 0) / maxLevelVal) * 100)}%` }}
                />
              </div>
            </div>
          ))}
        </div>

        {/* Recent Quizzes */}
        <div className="v-card" style={{ display: "grid", gap: 10 }}>
          <h3 style={{ margin: 0, fontSize: 17 }}>Recent Quizzes</h3>
          {recentQuizzes.length === 0 ? (
            <p style={{ margin: 0, color: "var(--v-text-muted)", fontSize: 14 }}>No quizzes completed yet.</p>
          ) : (
            recentQuizzes.slice(0, 6).map((q) => (
              <div
                key={q.id}
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  padding: 10,
                  borderRadius: 10,
                  background: "var(--v-bg)",
                }}
              >
                <div>
                  <strong style={{ fontSize: 14 }}>{q.quizType}</strong>
                  <div style={{ fontSize: 12, color: "var(--v-text-muted)" }}>
                    {q.correctAnswers}/{q.totalQuestions} correct
                  </div>
                </div>
                <span className={`v-pill ${q.score >= 70 ? "v-pill-green" : "v-pill-amber"}`}>
                  {q.score}%
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
