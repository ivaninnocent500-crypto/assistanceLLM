# Decision Assistant — Ktor + Claude

A minimal Ktor backend that **explains** (never decides) a travel operator
recommendation using Anthropic Claude. The choice is made upstream and passed
in via `recommendedTourId`; this service only produces natural-language
justification for that single tour.

## Endpoint

```
POST /v1/decision-assistant/explain
```

Request body (JSON):

```json
{
  "priorities": ["PRICE", "PRIVACY"],
  "recommendedTourId": "<uuid>",
  "tours": [
    {
      "tourId": "<uuid>",
      "operatorName": "Alpine Guides",
      "pricePerPerson": 1199.00,
      "inclusionsCount": 6,
      "operatorRating": 4.7,
      "operatorReviewCount": 1280,
      "guideToTravelerRatio": "1:4",
      "emergencySupport24_7": true,
      "freeCancelDays": 14,
      "sustainabilityScore": 82
    }
  ]
}
```

Success response body:

```json
{
  "explanation": "2-3 sentences ...",
  "keyPoints": ["fact 1", "fact 2", "fact 3"]
}
```

On ANY failure (timeout, malformed model output, rate limit, missing
`recommendedTourId`, missing API key) the server returns **HTTP 503 with an
empty body**, by design — the caller can gracefully fall back to a
no-explanation path.

## Build & run locally

```bash
# JDK 21 required
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew shadowJar
java -jar build/libs/decision-assistant-all.jar
# or
PORT=9090 ./gradlew run
```

## Container build

```bash
docker build -t decision-assistant:0.1 .
docker run --rm -p 8080:8080 \
    -e ANTHROPIC_API_KEY=sk-ant-... \
    -e PORT=8080 \
    decision-assistant:0.1
```

## Deploy to Cloud Run / Fly.io / Render

```bash
# Cloud Run example
gcloud run deploy decision-assistant \
    --source . \
    --region us-central1 \
    --set-env-vars "PORT=8080" \
    --set-secrets "ANTHROPIC_API_KEY=ANTHROPIC_API_KEY:latest" \
    --allow-unauthenticated
```

Cloud Run sends `PORT` automatically; the service reads it from the env
variable. Fly.io does the same. The container uses JRE 21 and honours the
`$PORT` env var with a default of `8080`.

## Configuration

| Env var                | Default  | Purpose                                        |
|------------------------|----------|------------------------------------------------|
| `PORT`                 | `8080`   | HTTP port                                      |
| `ANTHROPIC_API_KEY`    | _none_   | Required for live LLM calls. Never sent to clients. |
| `RATE_LIMIT_PER_MINUTE`| `30`     | Per-IP cap on `/explain` per rolling minute     |
| `RATE_LIMIT_PER_HOUR`  | `200`    | Per-IP cap on `/explain` per rolling hour       |

The rate limiter is in-memory and per-instance — fine for a single-instance or
edge deployment, but should be swapped for a shared store (Redis) when
horizontally scaled.

## Model

`claude-sonnet-4-6` is referenced exactly as the requesting system asked. If
Anthropic renames that alias, swap `AppConfig.MODEL` in
`com.tta.decisionassistant.Application` (and the `model` parameter on
`ClaudeClient`/`DecisionAssistantService`) to a current model id.
The Anthropic Messages API version header is pinned to `2023-06-01`.

## Behaviour guarantees

* The model never sees priority definitions or comparisons between tours —
  the system prompt forbids comparing and the user prompt only carries the
  chosen tour's facts.
* The model output is constrained to a single `EXPLANATION:` paragraph and a
  `POINTS:` bullet list, parsed by `ResponseParser`. Any deviation is treated
  as a failure and the caller gets 503.
* All Anthropic HTTP traffic has a 15 s request timeout. Timeouts, non-2xx
  responses, and parse failures all collapse to 503.
* The API key is read from env once at startup, held only in process memory,
  and never serialized into request bodies.

## Layout

```
src/main/kotlin/com/tta/decisionassistant/
├── Application.kt                        # embeddedServer(Netty), routing, failure → 503
├── model/Models.kt                       # @Serializable request/response + Anthropic DTOs
└── service/
    ├── ClaudeClient.kt                   # Ktor CIO client → Anthropic Messages API
    ├── DecisionAssistantService.kt       # orchestrates validate → call → parse
    ├── IpRateLimiter.kt                  # simple per-IP sliding window
    ├── PromptBuilder.kt                  # strict system + user prompts
    └── ResponseParser.kt                 # deterministic EXPLANATION:/POINTS: parser
```
