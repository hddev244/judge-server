/**
 * AdminProblems — React component for managing problems via the backend proxy.
 *
 * The API key is stored in server.js (Node/Express), not here.
 * This component calls /api/judge/* on its own backend which proxies to the judge server.
 *
 * Usage:
 *   <AdminProblems />
 *
 * Requires: React 18+
 */

import { useState, useEffect, useCallback } from 'react';

const API = '/api/judge';

async function judgeApi(path, opts = {}) {
  const res = await fetch(API + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`);
  return data;
}

// ─── Difficulty badge ────────────────────────────────────────────
function DiffBadge({ diff }) {
  if (!diff) return null;
  const styles = {
    easy:   { background: 'rgba(34,197,94,.15)',   color: '#22c55e' },
    medium: { background: 'rgba(245,158,11,.15)',  color: '#f59e0b' },
    hard:   { background: 'rgba(239,68,68,.15)',   color: '#ef4444' },
  };
  return <span style={{ padding: '2px 8px', borderRadius: 5, fontSize: 11, fontWeight: 700, ...styles[diff] }}>{diff}</span>;
}

// ─── Create / Edit form ──────────────────────────────────────────
function ProblemForm({ initial = {}, onSave, onCancel, saving }) {
  const [form, setForm] = useState({
    slug: initial.slug || '',
    title: initial.title || '',
    description: initial.description || '',
    descriptionFormat: initial.descriptionFormat || 'MARKDOWN',
    timeLimitMs: initial.timeLimitMs || 2000,
    memoryLimitKb: initial.memoryLimitKb || 262144,
    difficulty: initial.difficulty || '',
    tags: (initial.tags || []).join(', '),
  });

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  function buildPayload() {
    return {
      slug: form.slug.trim(),
      title: form.title.trim(),
      description: form.description.trim(),
      descriptionFormat: form.descriptionFormat,
      timeLimitMs: +form.timeLimitMs,
      memoryLimitKb: +form.memoryLimitKb,
      difficulty: form.difficulty || null,
      tags: form.tags.split(',').map(s => s.trim()).filter(Boolean),
    };
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <label style={labelStyle}>
        Slug *
        <input style={inputStyle} value={form.slug} onChange={e => set('slug', e.target.value)} placeholder="a-plus-b" readOnly={!!initial.id} />
      </label>
      <label style={labelStyle}>
        Title *
        <input style={inputStyle} value={form.title} onChange={e => set('title', e.target.value)} placeholder="A + B Problem" />
      </label>
      <label style={labelStyle}>
        Description
        <textarea style={{ ...inputStyle, minHeight: 80, resize: 'vertical' }}
          value={form.description} onChange={e => set('description', e.target.value)} />
      </label>
      <label style={labelStyle}>
        Format
        <select style={inputStyle} value={form.descriptionFormat} onChange={e => set('descriptionFormat', e.target.value)}>
          <option value="MARKDOWN">Markdown</option>
          <option value="HTML">HTML</option>
        </select>
      </label>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <label style={labelStyle}>
          Time limit (ms)
          <input style={inputStyle} type="number" value={form.timeLimitMs} onChange={e => set('timeLimitMs', e.target.value)} />
        </label>
        <label style={labelStyle}>
          Memory limit (KB)
          <input style={inputStyle} type="number" value={form.memoryLimitKb} onChange={e => set('memoryLimitKb', e.target.value)} />
        </label>
        <label style={labelStyle}>
          Difficulty
          <select style={inputStyle} value={form.difficulty} onChange={e => set('difficulty', e.target.value)}>
            <option value="">— none —</option>
            <option value="easy">Easy</option>
            <option value="medium">Medium</option>
            <option value="hard">Hard</option>
          </select>
        </label>
        <label style={labelStyle}>
          Tags (phân cách bởi dấu phẩy)
          <input style={inputStyle} value={form.tags} onChange={e => set('tags', e.target.value)} placeholder="dp, greedy" />
        </label>
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8 }}>
        <button style={btnGhost} onClick={onCancel} disabled={saving}>Hủy</button>
        <button style={btnPrimary} onClick={() => onSave(buildPayload())} disabled={saving}>
          {saving ? 'Đang lưu…' : 'Lưu'}
        </button>
      </div>
    </div>
  );
}

// ─── Main component ──────────────────────────────────────────────
export default function AdminProblems() {
  const [problems, setProblems]   = useState([]);
  const [page, setPage]           = useState(0);
  const [totalPages, setTotal]    = useState(1);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState('');
  const [creating, setCreating]   = useState(false);
  const [editing, setEditing]     = useState(null);
  const [saving, setSaving]       = useState(false);

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    try {
      const data = await judgeApi(`/api/v1/admin/problems?page=${p}&size=20`);
      setProblems(data.content || []);
      setTotal(data.totalPages || 1);
      setPage(data.number || 0);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(0); }, [load]);

  async function handleCreate(form) {
    setSaving(true);
    try {
      await judgeApi('/api/v1/admin/problems', { method: 'POST', body: JSON.stringify(form) });
      setCreating(false);
      await load(0);
    } catch (e) { alert('Lỗi: ' + e.message); }
    finally { setSaving(false); }
  }

  async function handleUpdate(form) {
    setSaving(true);
    try {
      await judgeApi(`/api/v1/admin/problems/${editing.id}`, { method: 'PUT', body: JSON.stringify(form) });
      setEditing(null);
      await load(page);
    } catch (e) { alert('Lỗi: ' + e.message); }
    finally { setSaving(false); }
  }

  async function publish(id) {
    if (!window.confirm('Publish problem này?')) return;
    try {
      await judgeApi(`/api/v1/admin/problems/${id}/publish`, { method: 'POST' });
      await load(page);
    } catch (e) { alert('Lỗi: ' + e.message); }
  }

  if (loading) return <p style={{ color: '#8b8fa8' }}>Đang tải...</p>;
  if (error)   return <p style={{ color: '#ef4444' }}>Lỗi: {error}</p>;

  return (
    <div style={{ fontFamily: 'system-ui,sans-serif', color: '#e2e4f0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 18, fontWeight: 700 }}>📚 Problems</h2>
        <button style={btnPrimary} onClick={() => setCreating(true)}>+ Tạo problem</button>
      </div>

      {creating && (
        <div style={overlay}>
          <div style={modal}>
            <h3 style={{ marginBottom: 16 }}>📚 Tạo Problem mới</h3>
            <ProblemForm onSave={handleCreate} onCancel={() => setCreating(false)} saving={saving} />
          </div>
        </div>
      )}

      {editing && (
        <div style={overlay}>
          <div style={modal}>
            <h3 style={{ marginBottom: 16 }}>✏️ Chỉnh sửa Problem</h3>
            <ProblemForm initial={editing} onSave={handleUpdate} onCancel={() => setEditing(null)} saving={saving} />
          </div>
        </div>
      )}

      {problems.length === 0 ? (
        <p style={{ color: '#8b8fa8', textAlign: 'center', padding: 40 }}>Chưa có problem nào.</p>
      ) : (
        <>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #2e3148' }}>
                {['ID', 'Slug', 'Title', 'Limits', 'Difficulty', 'Status', ''].map(h => (
                  <th key={h} style={{ padding: '8px 12px', textAlign: 'left', color: '#8b8fa8', fontSize: 11 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {problems.map(p => (
                <tr key={p.id} style={{ borderBottom: '1px solid #1a1d27' }}>
                  <td style={td}>{p.id}</td>
                  <td style={td}><code style={{ fontSize: 11 }}>{p.slug}</code></td>
                  <td style={td}>{p.title}</td>
                  <td style={{ ...td, fontSize: 11, color: '#8b8fa8' }}>{p.timeLimitMs}ms / {Math.round(p.memoryLimitKb / 1024)}MB</td>
                  <td style={td}><DiffBadge diff={p.difficulty} /></td>
                  <td style={td}>
                    <span style={{
                      padding: '2px 8px', borderRadius: 5, fontSize: 11, fontWeight: 700,
                      ...(p.published
                        ? { background: 'rgba(34,197,94,.15)', color: '#22c55e' }
                        : { background: 'rgba(139,143,168,.15)', color: '#8b8fa8' })
                    }}>
                      {p.published ? 'Published' : 'Draft'}
                    </span>
                  </td>
                  <td style={td}>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button style={btnSmGhost} onClick={() => setEditing(p)}>✏️</button>
                      {!p.published && (
                        <button style={{ ...btnSmGhost, color: '#22c55e', borderColor: 'rgba(34,197,94,.3)' }} onClick={() => publish(p.id)}>
                          Publish
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 8, marginTop: 12 }}>
              <button style={btnSmGhost} onClick={() => load(page - 1)} disabled={page === 0}>‹ Prev</button>
              <span style={{ fontSize: 13, color: '#8b8fa8' }}>Trang {page + 1} / {totalPages}</span>
              <button style={btnSmGhost} onClick={() => load(page + 1)} disabled={page >= totalPages - 1}>Next ›</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ─── Shared styles ───────────────────────────────────────────────
const overlay    = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 };
const modal      = { background: '#1a1d27', border: '1px solid #2e3148', borderRadius: 14, padding: '24px 28px', width: '100%', maxWidth: 480, maxHeight: '90vh', overflowY: 'auto', color: '#e2e4f0' };
const inputStyle = { display: 'block', width: '100%', marginTop: 4, background: '#222536', border: '1px solid #2e3148', borderRadius: 8, padding: '8px 12px', color: '#e2e4f0', fontSize: 13, fontFamily: 'inherit', outline: 'none', boxSizing: 'border-box' };
const labelStyle = { display: 'block', fontSize: 11, fontWeight: 700, color: '#8b8fa8', textTransform: 'uppercase', letterSpacing: '.8px' };
const btnPrimary = { background: '#6c63ff', color: '#fff', border: 'none', borderRadius: 8, padding: '7px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' };
const btnGhost   = { background: 'transparent', color: '#8b8fa8', border: '1px solid #2e3148', borderRadius: 8, padding: '7px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' };
const btnSmGhost = { background: 'transparent', color: '#8b8fa8', border: '1px solid #2e3148', borderRadius: 6, padding: '4px 8px', fontSize: 12, cursor: 'pointer' };
const td         = { padding: '10px 12px', verticalAlign: 'middle' };
