#!/usr/bin/env node
/**
 * DSH-MOBILE Link · Cloudflare quick tunnel helper.
 *
 *   node public-tunnel.mjs --target http://127.0.0.1:3091 --cloudflared ./cloudflared.exe
 *
 * Prints the https://*.trycloudflare.com URL once the tunnel is reachable,
 * then stays in the foreground. Ctrl+C closes the tunnel.
 */
import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'

const argv = process.argv.slice(2)
const arg = (name, fallback) => {
  const i = argv.indexOf('--' + name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback
}
const TARGET = arg('target', 'http://127.0.0.1:3091')
const CLOUDFLARED = path.resolve(arg('cloudflared', path.join(process.cwd(), 'cloudflared.exe')))

if (!fs.existsSync(CLOUDFLARED)) {
  console.error(`cloudflared not found: ${CLOUDFLARED}`)
  console.error('Download: https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe')
  process.exit(2)
}

const child = spawn(CLOUDFLARED, ['tunnel', '--url', TARGET, '--no-autoupdate', '--loglevel', 'info'], {
  stdio: ['ignore', 'pipe', 'pipe'],
  windowsHide: true,
})

let settled = false
let buffer = ''
const timer = setTimeout(() => {
  if (!settled) {
    console.error('timed out waiting for a public URL')
    child.kill()
    process.exit(1)
  }
}, 60_000)

async function probe(url) {
  for (let i = 0; i < 120; i++) {
    try {
      const response = await fetch(url, { redirect: 'manual' })
      if (response.status < 500) {
        settled = true
        clearTimeout(timer)
        console.log(`PUBLIC_URL=${url}`)
        return
      }
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
}

let probing = false
function scan(chunk) {
  const text = chunk.toString()
  buffer += text
  process.stderr.write(text)
  const match = buffer.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i)
  if (match && !probing) {
    probing = true
    probe(match[0])
  }
}

child.stdout.on('data', scan)
child.stderr.on('data', scan)
child.on('error', (error) => {
  console.error(error.message)
  process.exit(1)
})
child.on('exit', (code) => {
  if (!settled) {
    console.error(`cloudflared exited: ${code}`)
    process.exit(1)
  }
})
process.on('SIGINT', () => {
  child.kill()
  process.exit(0)
})
