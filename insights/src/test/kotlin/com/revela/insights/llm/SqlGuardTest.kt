package com.revela.insights.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SqlGuardTest {

    @Test
    fun `plain select over a rollup table is allowed`() {
        assertNull(SqlGuard.validate("SELECT date, total_screen_time_s FROM day_summary ORDER BY date"))
        assertNull(SqlGuard.validate("select app_pkg, sum(total_seconds) from usage_daily group by app_pkg;"))
        assertNull(
            SqlGuard.validate(
                "SELECT d.date, u.app_pkg FROM day_summary d JOIN usage_daily u ON d.date = u.date",
            ),
        )
    }

    @Test
    fun `the raw event log is never queryable`() {
        assertNotNull(SqlGuard.validate("SELECT * FROM events"))
        assertNotNull(SqlGuard.validate("SELECT * FROM day_summary JOIN events ON 1=1"))
        assertNotNull(SqlGuard.validate("SELECT (SELECT count(*) FROM events)"))
    }

    @Test
    fun `writes and schema statements are rejected`() {
        assertNotNull(SqlGuard.validate("DELETE FROM day_summary"))
        assertNotNull(SqlGuard.validate("DROP TABLE insights"))
        assertNotNull(SqlGuard.validate("INSERT INTO insights VALUES (1)"))
        assertNotNull(SqlGuard.validate("PRAGMA table_info(day_summary)"))
        assertNotNull(SqlGuard.validate("SELECT 1; DELETE FROM day_summary"))
    }

    @Test
    fun `other internal tables are rejected`() {
        assertNotNull(SqlGuard.validate("SELECT * FROM watermarks"))
        assertNotNull(SqlGuard.validate("SELECT * FROM llm_audit"))
    }

    @Test
    fun `name-bearing tables are never queryable (D4)`() {
        assertNotNull(SqlGuard.validate("SELECT text FROM insights"))
        assertNotNull(SqlGuard.validate("SELECT * FROM comms_daily"))
        assertNotNull(SqlGuard.validate("SELECT * FROM place_daily"))
        assertNotNull(SqlGuard.validate("SELECT * FROM day_summary JOIN insights ON 1=1"))
    }

    @Test
    fun `tableless selects are rejected`() {
        assertNotNull(SqlGuard.validate("SELECT 1"))
    }
}
