package com.kamboji.quiver.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The failure modes covered here are the ones observed from real small-model
 * output: markdown fences, prose wrapping, truncation, raw newlines inside
 * strings, and a lost closing quote. If one of these starts failing, the agent
 * regresses to "Sorry — garbled plan" answers.
 */
class PlanJsonTest {

    private val goodPlan =
        """{"steps":[{"tool":"add_note","args":{"title":"Groceries","body":"- [ ] Milk"}}],"reply":"Saved."}"""

    @Test
    fun `parses clean json`() {
        val plan = PlanJson.extract(goodPlan)
        assertNotNull(plan)
        assertEquals("Saved.", plan!!.getString("reply"))
        assertEquals("add_note", plan.getJSONArray("steps").getJSONObject(0).getString("tool"))
    }

    @Test
    fun `parses json inside markdown fences`() {
        assertNotNull(PlanJson.extract("```json\n$goodPlan\n```"))
    }

    @Test
    fun `parses json wrapped in prose`() {
        val plan = PlanJson.extract("Sure! Here is the plan:\n$goodPlan\nHope that helps!")
        assertNotNull(plan)
        assertEquals("Saved.", plan!!.getString("reply"))
    }

    @Test
    fun `repairs truncated output`() {
        val truncated = """{"steps":[{"tool":"add_task","args":{"title":"Call mom","date":"2027-01-0"""
        val plan = PlanJson.extract(truncated)
        assertNotNull(plan)
        assertEquals("add_task", plan!!.getJSONArray("steps").getJSONObject(0).getString("tool"))
    }

    @Test
    fun `repairs raw newline inside a string`() {
        val raw = "{\"steps\":[],\"reply\":\"line one\nline two\"}"
        val plan = PlanJson.extract(raw)
        assertNotNull(plan)
        assertEquals("line one\nline two", plan!!.getString("reply"))
    }

    @Test
    fun `repairs missing closing quote`() {
        val raw = """{"steps":[],"reply":"unterminated}"""
        assertNotNull(PlanJson.extract(raw))
    }

    @Test
    fun `keeps nested braces inside strings intact`() {
        val raw = """{"steps":[],"reply":"use {curly} braces"}"""
        val plan = PlanJson.extract(raw)
        assertEquals("use {curly} braces", plan!!.getString("reply"))
    }

    @Test
    fun `returns null for pure prose`() {
        assertNull(PlanJson.extract("Okay! I will add the note and remind you tomorrow."))
    }

    @Test
    fun `returns null for empty input`() {
        assertNull(PlanJson.extract(""))
    }

    @Test
    fun `steps survive repair of a longer garbled plan`() {
        val raw = """{"steps":[{"tool":"add_note","args":{"title":"Pasta","body":"- [ ] Tomatoes
- [ ] Garlic"}},{"tool":"add_task","args":{"title":"Buy","date":"2027-05-10","time":"14:00"}}],"reply":"Done"""
        val plan = PlanJson.extract(raw)
        assertNotNull(plan)
        assertEquals(2, plan!!.getJSONArray("steps").length())
    }
}
