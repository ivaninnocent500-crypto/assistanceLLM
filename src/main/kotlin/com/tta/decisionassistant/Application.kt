package com.tta.decisionassistant

import com.tta.decisionassistant.model.ExplainRequest
import com.tta.decisionassistant.model.ExplainResponse
import com.tta.decisionassistant.service.DecisionAssistantService
import com.tta.decisionassistant.service.IpRateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val log = LoggerFactory.getLogger("Application")

object AppConfig {
    val port: Int = (System.getenv("PORT") ?: "8080").toInt()
    val apiKey: String = System.getenv("ANTHROPIC_API_KEY").orEmpty()
    val perMinute: Int = (System.getenv("RATE_LIMIT_PER_MINUTE") ?: "30").toInt()
    val perHour: Int = (System.getenv("RATE_LIMIT_PER_HOUR") ?: "200").toInt()
    const val MODEL: String = "claude-sonnet-4-6"
}

fun main() {
    if (AppConfig.apiKey.isBlank()) {
        log.warn(
            "ANTHROPIC_API_KEY is not set \u2014 every explain() call will fail and return HTTP 503. " +
                "Set the env var before deploying to a real environment."
        )
    }

    val service = DecisionAssistantService(apiKey = AppConfig.apiKey, model = AppConfig.MODEL)
    val rateLimiter = IpRateLimiter(perMinute = AppConfig.perMinute, perHour = AppConfig.perHour)

    embeddedServer(Netty, port = AppConfig.port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            })
        }
        install(CallLogging) {
            level = Level.INFO
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Unhandled exception on ${call.request.path()}", cause)
                call.respond(HttpStatusCode.ServiceUnavailable, "")
            }
        }
        routing {
            get("/healthz") { call.respondText("ok") }

            post("/v1/decision-assistant/explain") {
                val callerIp = resolveCallerIp(call)

                if (!rateLimiter.allow(callerIp)) {
                    log.info("Rate limit exceeded for $callerIp")
                    call.respond(HttpStatusCode.ServiceUnavailable, "")
                    return@post
                }

                val req: ExplainRequest = try {
                    call.receive<ExplainRequest>()
                } catch (t: Throwable) {
                    log.warn("Malformed explain request: ${t.message}")
                    call.respond(HttpStatusCode.ServiceUnavailable, "")
                    return@post
                }

                val outcome: Result<ExplainResponse> = service.explain(req)
                outcome.fold(
                    onSuccess = { resp -> call.respond(HttpStatusCode.OK, resp) },
                    onFailure = { err ->
                        log.warn("Explain failed: ${err.message}")
                        call.respond(HttpStatusCode.ServiceUnavailable, "")
                    }
                )
            }
        }
    }.start(wait = true)
}

/**
 * Best-effort caller IP. Trusts the first hop of `X-Forwarded-For` when present
 * (Cloud Run / Fly terminate TLS in front of us and forward the real client),
 * otherwise falls back to the local socket peer.
 */
private fun resolveCallerIp(call: io.ktor.server.application.ApplicationCall): String {
    val xff = call.request.headers["X-Forwarded-For"]
    if (!xff.isNullOrBlank()) {
        val first = xff.split(',').firstOrNull()?.trim().orEmpty()
        if (first.isNotEmpty()) return first
    }
    val real = call.request.headers["X-Real-IP"]
    if (!real.isNullOrBlank()) return real
    return call.request.local.remoteHost
}
