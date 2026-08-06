package io.sanato.apptemplate.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {
    @Test
    fun `default state is empty`() {
        assertTrue(UiState<String>().isEmpty)
    }

    @Test
    fun `loading with previous data is not empty`() {
        val state = UiState.loading(previous = "cached")
        assertFalse(state.isEmpty)
        assertTrue(state.isLoading)
    }

    @Test
    fun `success is not empty and not loading`() {
        val state = UiState.success("data")
        assertFalse(state.isEmpty)
        assertFalse(state.isLoading)
    }

    @Test
    fun `failure without previous data is not empty because error is set`() {
        val state = UiState.failure<String>(IllegalStateException("boom"))
        assertFalse(state.isEmpty)
    }
}
