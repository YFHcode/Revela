package com.revela.insights.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationValidatorTest {

    private val template =
        "On weekends, your day starts around 08:03 — about 59 minutes later than on weekdays (07:04)."

    @Test
    fun `rewrite keeping all numbers passes`() {
        assertTrue(
            NarrationValidator.keepsNumbers(
                template,
                "Your weekend mornings begin near 08:03, roughly 59 minutes after your weekday 07:04 start.",
            ),
        )
    }

    @Test
    fun `rewrite dropping a number is rejected`() {
        assertFalse(
            NarrationValidator.keepsNumbers(
                template,
                "Your weekend mornings start about an hour later than weekdays.",
            ),
        )
    }

    @Test
    fun `rewrite altering a time is rejected`() {
        assertFalse(
            NarrationValidator.keepsNumbers(
                template,
                "Weekends start near 08:30, about 59 minutes later than weekdays (07:04).",
            ),
        )
    }

    @Test
    fun `template without numbers accepts anything`() {
        assertTrue(NarrationValidator.keepsNumbers("You often switch apps.", "Quite the switcher."))
    }
}
