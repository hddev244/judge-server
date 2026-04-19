import { useState, useRef } from 'react';
import Editor from '@monaco-editor/react';

const LANGUAGES = [
  { label: 'C++',    value: 'cpp',    monaco: 'cpp' },
  { label: 'Java',   value: 'java',   monaco: 'java' },
  { label: 'Python', value: 'python', monaco: 'python' },
];

const VERDICT_COLORS = {
  AC:  '#22c55e',
  WA:  '#ef4444',
  TLE: '#f97316',
  MLE: '#a855f7',
  RE:  '#eab308',
  CE:  '#6b7280',
};

const PENDING_STATUSES = new Set(['PENDING', 'JUDGING']);

/**
 * Drop-in judge widget.
 *
 * Props:
 *   judgeUrl   — base URL of judge-server, e.g. "http://localhost:8080"
 *   apiKey     — X-API-Key value
 *   problemId  — problem ID (number)
 *   onVerdict  — optional callback(verdict: string, result: object)
 *
 * Install deps:
 *   npm install @monaco-editor/react
 */
export default function JudgeWidget({ judgeUrl, apiKey, problemId, onVerdict }) {
  const [language, setLanguage] = useState('cpp');
  const [source, setSource]     = useState('');
  const [status, setStatus]     = useState(null);   // null | 'submitting' | verdict string
  const [result, setResult]     = useState(null);
  const pollRef = useRef(null);

  const headers = { 'Content-Type': 'application/json', 'X-API-Key': apiKey };

  async function submit() {
    if (status === 'submitting' || PENDING_STATUSES.has(status)) return;

    setStatus('submitting');
    setResult(null);

    let submissionId;
    try {
      const res = await fetch(`${judgeUrl}/api/v1/submissions`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ problemId, language, sourceCode: source }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Submit failed');
      submissionId = data.submissionId;
    } catch (err) {
      setStatus('SE');
      setResult({ errorMessage: err.message });
      return;
    }

    setStatus('PENDING');
    poll(submissionId);
  }

  function poll(submissionId) {
    let attempts = 0;
    pollRef.current = setInterval(async () => {
      attempts++;
      try {
        const res  = await fetch(`${judgeUrl}/api/v1/submissions/${submissionId}`, { headers });
        const data = await res.json();
        if (!PENDING_STATUSES.has(data.status)) {
          clearInterval(pollRef.current);
          setStatus(data.status);
          setResult(data);
          onVerdict?.(data.status, data);
        } else {
          setStatus(data.status);
        }
      } catch {
        if (attempts >= 40) {
          clearInterval(pollRef.current);
          setStatus('SE');
          setResult({ errorMessage: 'Lost connection to judge server' });
        }
      }
    }, 1500);
  }

  const isPending  = status === 'submitting' || PENDING_STATUSES.has(status);
  const verdictColor = VERDICT_COLORS[status] ?? '#94a3b8';

  return (
    <div style={{ fontFamily: 'sans-serif', maxWidth: 900, margin: '0 auto' }}>
      {/* Toolbar */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
        <select
          value={language}
          onChange={e => setLanguage(e.target.value)}
          disabled={isPending}
          style={{ padding: '4px 8px', borderRadius: 4, border: '1px solid #475569', background: '#1e293b', color: '#f8fafc', cursor: 'pointer' }}
        >
          {LANGUAGES.map(l => (
            <option key={l.value} value={l.value}>{l.label}</option>
          ))}
        </select>

        <button
          onClick={submit}
          disabled={isPending || !source.trim()}
          style={{
            padding: '4px 20px',
            borderRadius: 4,
            border: 'none',
            background: isPending ? '#475569' : '#3b82f6',
            color: '#fff',
            cursor: isPending ? 'not-allowed' : 'pointer',
            fontWeight: 600,
          }}
        >
          {isPending ? 'Judging…' : 'Submit'}
        </button>

        {/* Spinner */}
        {isPending && (
          <span style={{ display: 'inline-block', width: 16, height: 16, border: '2px solid #94a3b8', borderTopColor: '#3b82f6', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
        )}

        {/* Verdict badge */}
        {status && !isPending && (
          <span style={{ fontWeight: 700, color: verdictColor, fontSize: 15 }}>{status}</span>
        )}
      </div>

      {/* Editor */}
      <Editor
        height="380px"
        theme="vs-dark"
        language={LANGUAGES.find(l => l.value === language)?.monaco}
        value={source}
        onChange={v => setSource(v ?? '')}
        options={{ minimap: { enabled: false }, fontSize: 14, scrollBeyondLastLine: false }}
      />

      {/* Result details */}
      {result && !isPending && (
        <div style={{ marginTop: 12, padding: 12, background: '#0f172a', borderRadius: 6, color: '#e2e8f0', fontSize: 13 }}>
          {result.timeMs   != null && <span style={{ marginRight: 16 }}>Time: {result.timeMs} ms</span>}
          {result.memoryKb != null && <span style={{ marginRight: 16 }}>Memory: {result.memoryKb} KB</span>}
          {result.score    != null && <span>Score: {result.score}</span>}
          {result.errorMessage && (
            <pre style={{ marginTop: 8, color: '#fca5a5', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
              {result.errorMessage}
            </pre>
          )}
        </div>
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
