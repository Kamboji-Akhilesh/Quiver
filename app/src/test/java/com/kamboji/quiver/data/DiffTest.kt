package com.kamboji.quiver.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffTest {
    private data class Row(val id: Long, val v: String)

    @Test fun onlyChangedAddedAndRemovedRowsAreWritten() {
        val old = listOf(Row(1, "a"), Row(2, "b"), Row(3, "c"))
        val new = listOf(Row(1, "a"), Row(2, "B"), Row(4, "d"))
        val (changed, removed) = diff(old, new) { it.id }
        assertEquals(listOf(Row(2, "B"), Row(4, "d")), changed)
        assertEquals(listOf(Row(3, "c")), removed)
    }

    @Test fun identicalListsWriteNothing() {
        val rows = listOf(Row(1, "a"), Row(2, "b"))
        assertEquals(emptyList<Row>() to emptyList<Row>(), diff(rows, rows.reversed()) { it.id })
    }
}
