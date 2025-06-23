package dev.rubentxu.hodei.pipelines.dsl.cli

import org.junit.jupiter.api.Test

class SimpleTest {
    
    @Test
    fun `simple test should pass`() {
        println("Simple test is running")
        assert(true) { "This should always pass" }
    }
}