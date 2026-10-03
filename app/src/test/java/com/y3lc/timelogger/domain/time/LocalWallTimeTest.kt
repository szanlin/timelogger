package com.y3lc.timelogger.domain.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalWallTimeTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun ordinaryWallTimeResolvesToUtc() {
        assertEquals(
            LocalWallTimeResult.Resolved(Instant.parse("2026-01-01T15:00:00Z")),
            resolveLocalWallTime(LocalDateTime.parse("2026-01-01T10:00:00"), zone),
        )
    }

    @Test
    fun repeatedWallTimeRequiresExplicitOffsetAndHonorsBothChoices() {
        val local = LocalDateTime.parse("2026-11-01T01:30:00")
        assertEquals(
            LocalWallTimeResult.Ambiguous(listOf(ZoneOffset.of("-04:00"), ZoneOffset.of("-05:00"))),
            resolveLocalWallTime(local, zone),
        )
        assertEquals(LocalWallTimeResult.Resolved(Instant.parse("2026-11-01T05:30:00Z")), resolveLocalWallTime(local, zone, ZoneOffset.of("-04:00")))
        assertEquals(LocalWallTimeResult.Resolved(Instant.parse("2026-11-01T06:30:00Z")), resolveLocalWallTime(local, zone, ZoneOffset.of("-05:00")))
        assertEquals(LocalWallTimeResult.InvalidOffset, resolveLocalWallTime(local, zone, ZoneOffset.UTC))
    }

    @Test
    fun nonexistentWallTimeIsRejectedInsteadOfShiftedForward() {
        assertEquals(LocalWallTimeResult.Nonexistent, resolveLocalWallTime(LocalDateTime.parse("2026-03-08T02:30:00"), zone))
    }

    @Test
    fun timeOutsideUtcMillisecondStorageRangeIsRejected() {
        assertEquals(LocalWallTimeResult.OutOfRange, resolveLocalWallTime(LocalDateTime.MAX, ZoneOffset.UTC))
        assertEquals(LocalWallTimeResult.OutOfRange, resolveLocalWallTime(LocalDateTime.MIN, ZoneOffset.UTC))
    }
}
