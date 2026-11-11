#!/usr/bin/env node
/**
 * DSH-MOBILE Link · standalone sync server (protocol v1).
 *
 * Exposes only the session-sync API; it does not proxy the DSH Web UI.
 *
 *   node sync-server.mjs --root ./sessions --port 3093 --pw <password>
 *
 * Endpoints:
 *   GET  /__dshm/sync/ping
 *   GET  /__dshm/sync/manifest
 *   GET  /__dshm/sync/file?path=...
 *   PUT  /__dshm/sync/file?path=...
 * Auth: x-dshm-sync: <password>
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

const argv = process.argv.slice(2)
const arg = (name, fallback) => {
  const i = argv.indexOf('--' + name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback
}
const PORT = Number(arg('port', '3093'))
const PW = arg('pw', process.env.DSH_SYNC_PW || '')
const ROOT = path.resolve(arg('root', path.join(process.cwd(), 'sessions')))
const MAX_BYTES = 64 * 1024 * 1024

if (!PW) {
  console.error('refusing to start: pass --pw or DSH_SYNC_PW')
  process.exit(2)
}
fs.mkdirSync(ROOT, { recursive: true })

function relPath(raw) {
  let rel
  try { rel = decodeURIComponent(raw || '') } catch { return null }
  rel = rel.replace(/\\/g, '/').replace(/^\/+/, '')
  if (!rel || rel.split('/').some((p) => p === '' || p === '..')) return null
  const full = path.resolve(ROOT, rel)
  if (full !== ROOT && !full.startsWith(ROOT + path.sep)) return null
  return full
}

function sha256File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha256')
    fs.createReadStream(file).on('data', (c) => h.update(c)).on('error', reject).on('end', () => resolve(h.digest('hex')))
  })
}

async function manifest() {
  const files = []
  async function walk(dir) {
    let entries
    try { entries = await fs.promises.readdir(dir, { withFileTypes: true }) } catch { return }
    for (const entry of entries) {
      if (entry.name.startsWith('.') || entry.name.endsWith('.tmp')) continue
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) { await walk(full); continue }
      if (!entry.isFile() || !entry.name.endsWith('.jsonl')) continue
      const stat = await fs.promises.stat(full)
      files.push({
        path: path.relative(ROOT, full).split(path.sep).join('/'),
        size: stat.size,
        mtimeMs: Math.round(stat.mtimeMs),
        sha256: await sha256File(full),
      })
    }
  }
  await walk(ROOT)
  files.sort((a, b) => a.path.localeCompare(b.path))
  return { version: 1, root: 'sessions', count: files.length, totalBytes: files.reduce((n, f) => n + f.size, 0), files }
}

function json(res, status, value) {
  const body = Buffer.from(JSON.stringify(value))
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': body.length, 'Cache-Control': 'no-store' })
  res.end(body)
}

const server = http.createServer(async (req, res) => {
  if (req.headers['x-dshm-sync'] !== PW) return json(res, 401, { error: 'sync-unauthorized' })
  const url = new URL(req.url || '/', 'http://sync.local')
  if (url.pathname === '/__dshm/sync/ping') return json(res, 200, { ok: true, version: 1, root: 'sessions' })
  if (url.pathname === '/__dshm/sync/manifest' && req.method === 'GET') {
    try { return json(res, 200, await manifest()) }
    catch (error) { return json(res, 500, { error: error.message }) }
  }
  if (url.pathname === '/__dshm/sync/file') {
    const full = relPath(url.searchParams.get('path'))
    if (!full) return json(res, 400, { error: 'bad-path' })
    if (req.method === 'GET') {
      try {
        const stat = await fs.promises.stat(full)
        if (!stat.isFile()) return json(res, 404, { error: 'not-found' })
        res.writeHead(200, { 'Content-Type': 'application/x-ndjson', 'Content-Length': stat.size, 'Cache-Control': 'no-store' })
        fs.createReadStream(full).pipe(res)
      } catch { return json(res, 404, { error: 'not-found' }) }
      return
    }
    if (req.method === 'PUT') {
      const chunks = []
      let size = 0
      for await (const chunk of req) {
        size += chunk.length
        if (size > MAX_BYTES) { res.writeHead(413); res.end(); return }
        chunks.push(chunk)
      }
      await fs.promises.mkdir(path.dirname(full), { recursive: true })
      const tmp = `${full}.tmp-${process.pid}-${Date.now()}`
      await fs.promises.writeFile(tmp, Buffer.concat(chunks))
      await fs.promises.rename(tmp, full)
      return json(res, 200, { ok: true, path: url.searchParams.get('path'), size })
    }
  }
  json(res, 404, { error: 'unknown-sync-endpoint' })
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[dsh-link] sync server 0.0.0.0:${PORT} root=${ROOT}`)
})
