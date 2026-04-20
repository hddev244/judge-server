/**
 * AdminContests — React component for managing contests via the backend proxy.
 *
 * The API key is stored in server.js (Node/Express), not here.
 * This component calls /api/judge/* on its own backend which proxies to the judge server.
 *
 * Usage:
 *   <AdminContests />
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

function fmtDt(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}

function toInputDt(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function toIso(local) {
  return local ? local + ':00' : null;
}

function StatusBadge({ status }) {
  const styles = {
    ONGOING:  { background: '#166534', color: '#86efac' },
    UPCOMING: { background: '#1e3a5f', color: '#93c5fd' },
    ENDED:    { background: '#1f2937', color: '#9ca3af' },
  };
  return (
    <span style={{ padding: '2px 8px', borderRadius: 5, fontSize: 11, fontWeight: 700, ...styles[status] }}>
      {status}
    </span>
  );
}

// ─── Create / Edit form ──────────────────────────────────────────
function ContestForm({ initial = {}, onSave, onCancel, saving }) {
  const [form, setForm] = useState({
    slug: initial.slug || '',
    title: initial.title || '',
    description: initial.description || '',
    startTime: toInputDt(initial.startTime),
    endTime: toInputDt(initial.endTime),
    isPublic: initial.isPublic || false,
  });

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <label style={labelStyle}>
        Slug *
        <input style={inputStyle} value={form.slug} onChange={e => set('slug', e.target.value)} placeholder="icpc-2025" />
      </label>
      <label style={labelStyle}>
        Title *
        <input style={inputStyle} value={form.title} onChange={e => set('title', e.target.value)} placeholder="ICPC 2025" />
      </label>
      <label style={labelStyle}>
        Description
        <textarea style={{ ...inputStyle, minHeight: 72, resize: 'vertical' }}
          value={form.description} onChange={e => set('description', e.target.value)} />
      </label>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <label style={labelStyle}>
          Bắt đầu *
          <input style={inputStyle} type="datetime-local" value={form.startTime} onChange={e => set('startTime', e.target.value)} />
        </label>
        <label style={labelStyle}>
          Kết thúc *
          <input style={inputStyle} type="datetime-local" value={form.endTime} onChange={e => set('endTime', e.target.value)} />
        </label>
      </div>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
        <input type="checkbox" checked={form.isPublic} onChange={e => set('isPublic', e.target.checked)} />
        Công khai
      </label>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8 }}>
        <button style={btnGhost} onClick={onCancel} disabled={saving}>Hủy</button>
        <button style={btnPrimary} onClick={() => onSave({ ...form, startTime: toIso(form.startTime), endTime: toIso(form.endTime) })} disabled={saving}>
          {saving ? 'Đang lưu…' : 'Lưu'}
        </button>
      </div>
    </div>
  );
}

// ─── Problems panel ──────────────────────────────────────────────
function ContestProblems({ contest, onClose }) {
  const [problems, setProblems] = useState(contest.problems || []);
  const [pid, setPid] = useState('');
  const [alias, setAlias] = useState('');
  const [order, setOrder] = useState(contest.problems?.length || 0);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const reload = useCallback(async () => {
    try {
      const list = await judgeApi('/api/v1/contests');
      const c = list.find(x => x.id === contest.id);
      if (c) { setProblems(c.problems || []); setOrder(c.problems?.length || 0); }
    } catch {}
  }, [contest.id]);

  async function add() {
    if (!pid) { setErr('Nhập Problem ID'); return; }
    setBusy(true); setErr('');
    try {
      await judgeApi(`/api/v1/admin/contests/${contest.id}/problems`, {
        method: 'POST',
        body: JSON.stringify({ problemId: +pid, alias: alias || null, orderIndex: +order }),
      });
      setPid(''); setAlias('');
      await reload();
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  }

  async function remove(problemId) {
    if (!window.confirm('Gỡ problem này?')) return;
    try {
      await judgeApi(`/api/v1/admin/contests/${contest.id}/problems/${problemId}`, { method: 'DELETE' });
      await reload();
    } catch (e) { setErr(e.message); }
  }

  return (
    <div style={overlay}>
      <div style={{ ...modal, maxWidth: 580 }}>
        <h3 style={{ marginBottom: 16 }}>📚 Problems — {contest.title}</h3>
        {problems.length > 0 ? (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, marginBottom: 16 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #2e3148' }}>
                {['Order', 'Alias', 'Problem', ''].map(h => (
                  <th key={h} style={{ padding: '6px 10px', textAlign: 'left', color: '#8b8fa8', fontSize: 11 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {problems.map(p => (
                <tr key={p.id} style={{ borderBottom: '1px solid #1a1d27' }}>
                  <td style={td}>{p.orderIndex}</td>
                  <td style={td}><span style={aliasBadge}>{p.alias || '—'}</span></td>
                  <td style={td}>{p.title} <code style={{ fontSize: 11, opacity: .7 }}>{p.slug}</code></td>
                  <td style={td}>
                    <button style={btnDanger} onClick={() => remove(p.problemId)}>🗑</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p style={{ color: '#8b8fa8', fontSize: 13, marginBottom: 16 }}>Chưa có problem nào.</p>
        )}
        <div style={{ borderTop: '1px solid #2e3148', paddingTop: 14 }}>
          <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 10 }}>Thêm problem</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
            <label style={labelStyle}>Problem ID *<input style={inputStyle} type="number" value={pid} onChange={e => setPid(e.target.value)} placeholder="1" /></label>
            <label style={labelStyle}>Alias<input style={inputStyle} value={alias} onChange={e => setAlias(e.target.value)} placeholder="A" maxLength={10} /></label>
          </div>
          <label style={labelStyle}>Order Index<input style={inputStyle} type="number" value={order} onChange={e => setOrder(e.target.value)} /></label>
          {err && <p style={{ color: '#ef4444', fontSize: 12, marginTop: 6 }}>{err}</p>}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
          <button style={btnGhost} onClick={onClose}>Đóng</button>
          <button style={btnPrimary} onClick={add} disabled={busy}>{busy ? '…' : '+ Thêm'}</button>
        </div>
      </div>
    </div>
  );
}

// ─── Main component ──────────────────────────────────────────────
export default function AdminContests() {
  const [contests, setContests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);
  const [managingProblems, setManagingProblems] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { setContests(await judgeApi('/api/v1/contests')); }
    catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function handleCreate(form) {
    setSaving(true);
    try {
      await judgeApi('/api/v1/admin/contests', { method: 'POST', body: JSON.stringify(form) });
      setCreating(false);
      await load();
    } catch (e) { alert('Lỗi: ' + e.message); }
    finally { setSaving(false); }
  }

  async function handleUpdate(form) {
    setSaving(true);
    try {
      await judgeApi(`/api/v1/admin/contests/${editing.id}`, { method: 'PUT', body: JSON.stringify(form) });
      setEditing(null);
      await load();
    } catch (e) { alert('Lỗi: ' + e.message); }
    finally { setSaving(false); }
  }

  if (loading) return <p style={{ color: '#8b8fa8' }}>Đang tải...</p>;
  if (error)   return <p style={{ color: '#ef4444' }}>Lỗi: {error}</p>;

  return (
    <div style={{ fontFamily: 'system-ui,sans-serif', color: '#e2e4f0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ fontSize: 18, fontWeight: 700 }}>🏅 Contests</h2>
        <button style={btnPrimary} onClick={() => setCreating(true)}>+ Tạo contest</button>
      </div>

      {creating && (
        <div style={overlay}>
          <div style={modal}>
            <h3 style={{ marginBottom: 16 }}>🏅 Tạo Contest mới</h3>
            <ContestForm onSave={handleCreate} onCancel={() => setCreating(false)} saving={saving} />
          </div>
        </div>
      )}

      {editing && (
        <div style={overlay}>
          <div style={modal}>
            <h3 style={{ marginBottom: 16 }}>✏️ Chỉnh sửa Contest</h3>
            <ContestForm initial={editing} onSave={handleUpdate} onCancel={() => setEditing(null)} saving={saving} />
          </div>
        </div>
      )}

      {managingProblems && (
        <ContestProblems contest={managingProblems} onClose={() => { setManagingProblems(null); load(); }} />
      )}

      {contests.length === 0 ? (
        <p style={{ color: '#8b8fa8', textAlign: 'center', padding: 40 }}>Chưa có contest nào.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #2e3148' }}>
              {['ID', 'Slug', 'Title', 'Thời gian', 'Status', 'Problems', ''].map(h => (
                <th key={h} style={{ padding: '8px 12px', textAlign: 'left', color: '#8b8fa8', fontSize: 11 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {contests.map(c => (
              <tr key={c.id} style={{ borderBottom: '1px solid #1a1d27' }}>
                <td style={td}>{c.id}</td>
                <td style={td}><code style={{ fontSize: 11 }}>{c.slug}</code></td>
                <td style={td}>{c.title}</td>
                <td style={{ ...td, fontSize: 11, color: '#8b8fa8' }}>
                  {fmtDt(c.startTime)}<br />{fmtDt(c.endTime)}
                </td>
                <td style={td}><StatusBadge status={c.status} /></td>
                <td style={td}>{(c.problems || []).length}</td>
                <td style={td}>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button style={btnSmGhost} onClick={() => setEditing(c)}>✏️</button>
                    <button style={btnSmGhost} onClick={() => setManagingProblems(c)}>📚</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ─── Shared styles ───────────────────────────────────────────────
const overlay = { position: 'fixed', inset: 0, background: 'rgba(0,0,0,.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 };
const modal   = { background: '#1a1d27', border: '1px solid #2e3148', borderRadius: 14, padding: '24px 28px', width: '100%', maxWidth: 480, maxHeight: '90vh', overflowY: 'auto', color: '#e2e4f0' };
const inputStyle = { display: 'block', width: '100%', marginTop: 4, background: '#222536', border: '1px solid #2e3148', borderRadius: 8, padding: '8px 12px', color: '#e2e4f0', fontSize: 13, fontFamily: 'inherit', outline: 'none', boxSizing: 'border-box' };
const labelStyle = { display: 'block', fontSize: 11, fontWeight: 700, color: '#8b8fa8', textTransform: 'uppercase', letterSpacing: '.8px' };
const btnPrimary = { background: '#6c63ff', color: '#fff', border: 'none', borderRadius: 8, padding: '7px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' };
const btnGhost   = { background: 'transparent', color: '#8b8fa8', border: '1px solid #2e3148', borderRadius: 8, padding: '7px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' };
const btnDanger  = { background: 'rgba(239,68,68,.15)', color: '#ef4444', border: '1px solid rgba(239,68,68,.3)', borderRadius: 6, padding: '3px 8px', fontSize: 12, cursor: 'pointer' };
const btnSmGhost = { background: 'transparent', color: '#8b8fa8', border: '1px solid #2e3148', borderRadius: 6, padding: '4px 8px', fontSize: 12, cursor: 'pointer' };
const td         = { padding: '10px 12px', verticalAlign: 'middle' };
const aliasBadge = { background: 'rgba(59,130,246,.15)', color: '#60a5fa', padding: '2px 7px', borderRadius: 5, fontSize: 11, fontWeight: 700 };
