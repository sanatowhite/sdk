package io.sanato.apptemplate.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun `map transforms success payload`() {
        val result: AppResult<Int> = AppResult.Success(1)

        val mapped = result.map { it + 1 }

        assertEquals(AppResult.Success(2), mapped)
    }

    @Test
    fun `map passes failure through untouched`() {
        val error = IllegalStateException("boom")
        val result: AppResult<Int> = AppResult.Failure(error)

        val mapped = result.map { it + 1 }

        assertEquals(AppResult.Failure(error), mapped)
    }

    @Test
    fun `onSuccess runs only for Success`() {
        var ran = false
        (AppResult.Success(1) as AppResult<Int>).onSuccess { ran = true }
        assertTrue(ran)

        ran = false
        (AppResult.Failure(RuntimeException()) as AppResult<Int>).onSuccess { ran = true }
        assertFalse(ran)
    }

    @Test
    fun `onFailure runs only for Failure`() {
        var ran = false
        (AppResult.Failure(RuntimeException()) as AppResult<Int>).onFailure { ran = true }
        assertTrue(ran)

        ran = false
        (AppResult.Success(1) as AppResult<Int>).onFailure { ran = true }
        assertFalse(ran)
    }
}
