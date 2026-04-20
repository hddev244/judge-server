/**
 * Express proxy server — API key lives here, NOT in the React frontend.
 *
 * Usage:
 *   JUDGE_API_KEY=sk_your_admin_key JUDGE_BASE_URL=https://your-judge-server node server.js
 *
 * Then in your React app call /api/judge/* instead of the judge server directly.
 *
 * Install: npm install express node-fetch@2
 */

const express = require('express');
const fetch   = require('node-fetch');
const path    = require('path');

const app  = express();
const PORT = process.env.PORT || 3001;

const JUDGE_BASE  = (process.env.JUDGE_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const JUDGE_KEY   = process.env.JUDGE_API_KEY;

if (!JUDGE_KEY) {
  console.error('ERROR: JUDGE_API_KEY env var is required');
  process.exit(1);
}

app.use(express.json());

// Serve React build (optional — only if you run `npm run build` first)
app.use(express.static(path.join(__dirname, 'build')));

// Proxy all /api/judge/* → judge server, injecting the API key server-side
app.all('/api/judge/*', async (req, res) => {
  const targetPath = req.path.replace('/api/judge', '');
  const url = JUDGE_BASE + targetPath + (req.url.includes('?') ? req.url.slice(req.url.indexOf('?')) : '');

  const headers = {
    'X-API-Key': JUDGE_KEY,
    'Content-Type': req.headers['content-type'] || 'application/json',
  };

  try {
    const upstream = await fetch(url, {
      method: req.method,
      headers,
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : JSON.stringify(req.body),
    });

    const text = await upstream.text();
    res.status(upstream.status)
       .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
       .send(text);
  } catch (err) {
    console.error('Proxy error:', err.message);
    res.status(502).json({ error: 'Bad Gateway', message: err.message });
  }
});

// SPA fallback
app.get('*', (_req, res) => res.sendFile(path.join(__dirname, 'build', 'index.html')));

app.listen(PORT, () => console.log(`Judge admin proxy running on http://localhost:${PORT}`));
