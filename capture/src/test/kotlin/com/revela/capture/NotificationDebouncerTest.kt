package com.revela.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDebouncerTest {

    @Test
    fun `first notification is recorded`() {
        val d = NotificationDebouncer()
        assertTrue(d.shouldRecord("com.whatsapp", "k1", 1000))
    }

    @Test
    fun `rapid update of same notification is suppressed`() {
        val d = NotificationDebouncer(windowMs = 10_000)
        d.shouldRecord("com.whatsapp", "k1", 1000)
        assertFalse(d.shouldRecord("com.whatsapp", "k1", 3000))
    }

    @Test
    fun `same key after the window records again`() {
        val d = NotificationDebouncer(windowMs = 10_000)
        d.shouldRecord("com.whatsapp", "k1", 1000)
        assertTrue(d.shouldRecord("com.whatsapp", "k1", 20_000))
    }

    @Test
    fun `different conversations are independent`() {
        val d = NotificationDebouncer()
        assertTrue(d.shouldRecord("com.whatsapp", "k1", 1000))
        assertTrue(d.shouldRecord("com.whatsapp", "k2", 1000))
        assertTrue(d.shouldRecord("com.telegram", "k1", 1000))
    }
}
