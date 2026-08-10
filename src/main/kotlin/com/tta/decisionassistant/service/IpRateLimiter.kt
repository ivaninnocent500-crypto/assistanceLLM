package com.tta.decisionassistant.service

import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.fixedRateTimer

/**
 * Simple in-memory IP rate limiter: caps each caller to [perMinute] requests in any
 * rolling 60-second window and [perHour] requests in any rolling hour.
 *
 * Adequate as a baseline for a single-instance deployment. For horizontally scaled
 * hosts (Cloud Run, Fly) this should be replaced with a shared store (Redis).
 */
class IpRateLimiter(
    private val perMinute: Int = 30,
    private val perHour: Int = 200,
    private val idleEvictionMillis: Long = 2 * 60 * 60_000L
) {
    private val state = ConcurrentHashMap<String, WindowState>()

    init {
        fixedRateTimer(name = "rate-limiter-gc", daemon = true, period = 5 * 60_000L) {
            val cutoff = System.currentTimeMillis() - idleEvictionMillis
            state.entries.removeIf { it.value.lastSeen < cutoff }
        }
    }

    fun allow(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val s = state.computeIfAbsent(ip) { WindowState(now) }
        synchronized(s) {
            if (now - s.minuteStart >= 60_000L) {
                s.minuteStart = now
                s.minuteCount = 0
            }
            if (now - s.hourStart >= 3_600_000L) {
                s.hourStart = now
                s.hourCount = 0
            }
            s.lastSeen = now
            if (s.minuteCount >= perMinute) return false
            if (s.hourCount >= perHour) return false
            s.minuteCount++
            s.hourCount++
            return true
        }
    }

    private class WindowState(val createdAt: Long) {
        var minuteStart: Long = createdAt
        var minuteCount: Int = 0
        var hourStart: Long = createdAt
        var hourCount: Int = 0
        var lastSeen: Long = createdAt
    }
}
