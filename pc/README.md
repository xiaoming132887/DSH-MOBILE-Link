# PC integration

## Sync server only

If you already have an HTTP server or only need session sync:

```bash
node pc/sync-server.mjs --root ./sessions --port 3093 --pw "long-random-password"
```

This exposes only `GET /__dshm/sync/ping`, `GET /__dshm/sync/manifest`,
`GET|PUT /__dshm/sync/file`.

## DSH WebUI proxy

If you want the phone browser to open the PC DSH WebUI:

```bash
# DSH_HOME points at the dsh home that contains sessions/, profiles/, etc.
set DSH_HOME=D:\DSH-MOBILE\data\home
node pc/dsh-web-proxy.mjs --port 3091 --pw "6-digit" --upstream 127.0.0.1:49760
```

Then use `pc/public-tunnel.mjs` for a public HTTPS URL:

```bash
node pc/public-tunnel.mjs \
  --target http://127.0.0.1:3091 \
  --cloudflared D:\DSH-MOBILE\runtime\cloudflared.exe
```

## Sync between two PCs

```bash
node pc/sync-client.mjs --url http://192.168.1.10:3093 --code "password" --local ./sessions
```

`--direction pull|push|both` and `--dry-run` are supported.

## Notes

- `sync-server.mjs` does not proxy the WebUI; it is the smallest integration.
- `dsh-web-proxy.mjs` patches the DSH frontend for non-loopback browsers
  (crypto.randomUUID, isLoopbackHostname, Host/Origin, WebSocket).
- Both servers reject paths outside the sessions root and cap file bodies at
  64 MiB.
