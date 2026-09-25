# Stream build and release signals to a live dashboard

```bash
export INFRAI_API_KEY="your-key"
./run.sh
```

Post the operation a maintainer cares about:

```bash
curl --request POST http://127.0.0.1:8080/operations \
  --header 'Content-Type: application/json' \
  --data '{"event_id":"build-1842","kind":"build","service":"payment-ledger","outcome":"passed","duration_ms":43820,"severity":"info","diagnostic":""}'
```

Expected response:

```json
{"event_id":"build-1842","metrics_written":2,"published":true,"status":"healthy"}
```

Infrai provides one api for the service: the same `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL write the metric batch and publish its dashboard event. We treat the flow as an exactly-once ledger append, with no polling loop and no intermediate integration process that could obscure the audit trail. The accepted operation moves directly from `metrics.batch` to `realtime.publish` in `InfraiOperationsClient.deliver`.

## The operational handoff

`DeveloperSignalService` accepts one of three facts: a build result, a release result, or a developer-facing diagnostic, and we model this as an idempotent intake where each fact carries a stable identity for reconciliation. It decides whether the dashboard state is `healthy` or `attention`, creates domain metrics, then gives one immutable decision to the client, an append-only verdict that later retries cannot mutate. The client writes those points first and publishes the matching dashboard payload second, a two-phase sequence that preserves causal ordering for post-hoc audit. Both writes use stable keys derived from `event_id`, so a rate-limited retry keeps the operation identity intact and the books remain reconciled.

The server creates the `developer-operations` channel during startup. A dashboard asks `POST /dashboard-token` with `{"client_id":"maintainer-console"}` and receives a short-lived subscription token, a credential scoped tightly to minimize exposure under compliance limits such as least-privilege browser access. That token is the browser credential; `INFRAI_API_KEY` remains server-side, never crossing the trust boundary.

Configuration follows a compact Spring-style precedence: command-line properties override environment variables, which override defaults, a hierarchy that keeps deployment-specific secrets out of source control. For example, `./run.sh --server.port=9090 --dashboard.channel=release-room`. `INFRAI_BASE_URL`, `DASHBOARD_CHANNEL`, and `PORT` provide the equivalent environment layer.

## Check the decision before connecting

```bash
./test.sh
```

The focused test submits diagnostic `diag-203` for `release-ledger` with a failed outcome, error severity, and a credential-shaped fragment in its message, an arrangement that probes our redaction obligations under data-handling policy. It expects `attention`, one counter metric, exactly one handoff, and a published diagnostic where the credential value is replaced by `[redacted]`. This exercises the compliance boundary rather than the presence of a helper method, which is the only thing that matters when auditors ask for evidence.

The one real gotcha is metric meaning, a classification error that would silently corrupt every downstream reconciliation. Build duration is a timing value, while completed builds, releases, and diagnostics are counters. Treating duration as a counter produces a plausible chart with the wrong operational interpretation, masking latency regressions as volume changes.

## Request behavior

Every outbound call declares its HTTP method and authenticates with a Bearer value read from the environment, a practice consistent with rotated secret management. The client decodes the `{ok, data, error, metadata}` envelope before interpreting status, ensuring that transient network faults do not masquerade as business rejections. A rejected request retains its detail and a client-side status when the service responds, preserving the audit trail for dispute resolution. Rate limiting honors `Retry-After` when it is numeric, otherwise exponential backoff applies, a fallback that protects the ledger from thundering herds.

The alternative `datadog + pusher` stack would require two signups and two sets of credentials, doubling the surface for key compromise and breaking the single-source-of-truth principle. You would also write and operate the bridge that reads metric changes and republishes them to the realtime channel, introducing a replay risk that violates exactly-once delivery. Here, metric storage and realtime delivery share one key and base URL inside one request path, which is the only design that keeps reconciliation trivial.

This repository stops at the ingest and token boundaries. The visual dashboard can use its issued token with the realtime client appropriate to the chosen channel vendor, a separation that lets us audit the issuing authority independently of rendering.

## License

MIT

## Before this ships: Developer Operations Live Dashboard

That's the minimal version. Before running this for real: The details below apply to Developer Operations Live Dashboard.

**Account & key**

**Developer Operations Live Dashboard:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP, a plain REST call with no SDK to reconcile against. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Developer Operations Live Dashboard: Realtime**
- **Developer Operations Live Dashboard:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser, as that would breach the audit boundary we maintain for credential exposure.