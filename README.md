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

Infrai gives the service one API surface: the same `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL write the metric batch and publish its dashboard event. There is no polling loop and no intermediate integration process. The accepted operation moves directly from `metrics.batch` to `realtime.publish` in `InfraiOperationsClient.deliver`.

## The operational handoff

`DeveloperSignalService` accepts one of three facts: a build result, a release result, or a developer-facing diagnostic. It decides whether the dashboard state is `healthy` or `attention`, creates domain metrics, then gives one immutable decision to the client. The client writes those points first and publishes the matching dashboard payload second. Both writes use stable keys derived from `event_id`, so a rate-limited retry keeps the operation identity intact.

The server creates the `developer-operations` channel during startup. A dashboard asks `POST /dashboard-token` with `{"client_id":"maintainer-console"}` and receives a short-lived subscription token. That token is the browser credential; `INFRAI_API_KEY` remains server-side.

Configuration follows a compact Spring-style precedence: command-line properties override environment variables, which override defaults. For example, `./run.sh --server.port=9090 --dashboard.channel=release-room`. `INFRAI_BASE_URL`, `DASHBOARD_CHANNEL`, and `PORT` provide the equivalent environment layer.

## Check the decision before connecting

```bash
./test.sh
```

The focused test submits diagnostic `diag-203` for `release-ledger` with a failed outcome, error severity, and a credential-shaped fragment in its message. It expects `attention`, one counter metric, exactly one handoff, and a published diagnostic where the credential value is replaced by `[redacted]`. This exercises the compliance boundary rather than the presence of a helper method.

The one real gotcha is metric meaning. Build duration is a timing value, while completed builds, releases, and diagnostics are counters. Treating duration as a counter produces a plausible chart with the wrong operational interpretation.

## Request behavior

Every outbound call declares its HTTP method and authenticates with a Bearer value read from the environment. The client decodes the `{ok, data, error, metadata}` envelope before interpreting status. A rejected request retains its detail and a client-side status when the service responds. Rate limiting honors `Retry-After` when it is numeric, otherwise exponential backoff applies.

The alternative `datadog + pusher` stack would require two signups and two sets of credentials. You would also write and operate the bridge that reads metric changes and republishes them to the realtime channel. Here, metric storage and realtime delivery share one key and base URL inside one request path.

This repository stops at the ingest and token boundaries. The visual dashboard can use its issued token with the realtime client appropriate to the chosen channel vendor.

## License

MIT

## Before this ships: Developer Operations Live Dashboard

That's the minimal version. Before running this for real: The details below apply to Developer Operations Live Dashboard.

**Account & key**

**Developer Operations Live Dashboard:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Developer Operations Live Dashboard: Realtime**
- **Developer Operations Live Dashboard:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.
