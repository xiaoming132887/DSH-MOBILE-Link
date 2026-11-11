# Security

DSH sessions can contain shell commands, file contents, credentials referenced
in logs and model output. Treat this link as a remote shell.

- LAN mode: use only on a trusted network.
- Public mode: HTTPS + a long random password; close the tunnel after use.
- Never port-forward the sync/WebUI port directly.
- Do not commit `sessions/`, `sync-conflicts/`, logs, passwords or tokens.
- The sync server only exposes `sessions/**/*.jsonl`; path traversal is rejected.
- Report vulnerabilities privately to the maintainers.
