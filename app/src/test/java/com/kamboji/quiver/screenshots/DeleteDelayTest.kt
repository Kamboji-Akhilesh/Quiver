package com.kamboji.quiver.screenshots

import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteDelayTest {
    @Test
    fun `singular and plural units read naturally`() {
        assertEquals("1 minute", DeleteDelay(1, DelayUnit.MINUTES).describe())
        assertEquals("5 minutes", DeleteDelay(5, DelayUnit.MINUTES).describe())
        assertEquals("1 hour", DeleteDelay(1, DelayUnit.HOURS).describe())
        assertEquals("2 days", DeleteDelay(2, DelayUnit.DAYS).describe())
    }
}
