package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ejecutor y reporter personalizado para tests E2E del CLI Pipeline DSL.
 * 
 * Proporciona métricas detalladas y reportes de los tests end-to-end.
 */
class E2ETestRunner {
    
    data class TestResults(
        val totalTests: Int,
        val successfulTests: Int,
        val failedTests: Int,
        val skippedTests: Int,
        val executionTime: Duration,
        val testDetails: List<TestDetail>
    ) {
        val successRate: Double = if (totalTests > 0) (successfulTests.toDouble() / totalTests) * 100 else 0.0
        val isAllPassed: Boolean = failedTests == 0 && totalTests > 0
    }
    
    data class TestDetail(
        val name: String,
        val className: String,
        val status: TestStatus,
        val executionTime: Duration,
        val errorMessage: String? = null
    )
    
    enum class TestStatus {
        SUCCESSFUL, FAILED, SKIPPED, ABORTED
    }
    
    class E2ETestExecutionListener : TestExecutionListener {
        private val testDetails = mutableListOf<TestDetail>()
        private val testTimings = mutableMapOf<TestIdentifier, Long>()
        
        override fun testPlanExecutionStarted(testPlan: TestPlan) {
            println("🚀 Starting Pipeline DSL CLI E2E Test Execution")
            println("📊 Total tests planned: ${testPlan.countTestIdentifiers { it.isTest }}")
            println("⏰ Started at: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}")
            println("=" * 60)
        }
        
        override fun executionStarted(testIdentifier: TestIdentifier) {
            if (testIdentifier.isTest) {
                testTimings[testIdentifier] = System.currentTimeMillis()
                println("🔄 Running: ${testIdentifier.displayName}")
            }
        }
        
        override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: org.junit.platform.engine.TestExecutionResult) {
            if (testIdentifier.isTest) {
                val startTime = testTimings[testIdentifier] ?: System.currentTimeMillis()
                val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
                
                val status = when (testExecutionResult.status) {
                    org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL -> TestStatus.SUCCESSFUL
                    org.junit.platform.engine.TestExecutionResult.Status.FAILED -> TestStatus.FAILED
                    org.junit.platform.engine.TestExecutionResult.Status.ABORTED -> TestStatus.ABORTED
                }
                
                val errorMessage = testExecutionResult.throwable.orElse(null)?.message
                
                val testDetail = TestDetail(
                    name = testIdentifier.displayName,
                    className = testIdentifier.parentId.orElse("Unknown"),
                    status = status,
                    executionTime = duration,
                    errorMessage = errorMessage
                )
                
                testDetails.add(testDetail)
                
                val statusIcon = when (status) {
                    TestStatus.SUCCESSFUL -> "✅"
                    TestStatus.FAILED -> "❌"
                    TestStatus.SKIPPED -> "⏭️"
                    TestStatus.ABORTED -> "🛑"
                }
                
                println("$statusIcon ${testIdentifier.displayName} (${duration.toMillis()}ms)")
                if (errorMessage != null) {
                    println("   Error: $errorMessage")
                }
            }
        }
        
        override fun testPlanExecutionFinished(testPlan: TestPlan) {
            println("=" * 60)
            generateReport()
        }
        
        fun getResults(): TestResults {
            val successful = testDetails.count { it.status == TestStatus.SUCCESSFUL }
            val failed = testDetails.count { it.status == TestStatus.FAILED }
            val skipped = testDetails.count { it.status == TestStatus.SKIPPED }
            val totalTime = testDetails.fold(Duration.ZERO) { acc, detail -> acc.plus(detail.executionTime) }
            
            return TestResults(
                totalTests = testDetails.size,
                successfulTests = successful,
                failedTests = failed,
                skippedTests = skipped,
                executionTime = totalTime,
                testDetails = testDetails.toList()
            )
        }
        
        private fun generateReport() {
            val results = getResults()
            
            println("📊 Pipeline DSL CLI E2E Test Report")
            println("=" * 60)
            println("📈 Summary:")
            println("   Total Tests: ${results.totalTests}")
            println("   ✅ Successful: ${results.successfulTests}")
            println("   ❌ Failed: ${results.failedTests}")
            println("   ⏭️ Skipped: ${results.skippedTests}")
            println("   📊 Success Rate: ${"%.1f".format(results.successRate)}%")
            println("   ⏱️ Total Execution Time: ${results.executionTime.toSeconds()}s")
            println()
            
            if (results.failedTests > 0) {
                println("❌ Failed Tests:")
                results.testDetails.filter { it.status == TestStatus.FAILED }.forEach { test ->
                    println("   • ${test.name}")
                    test.errorMessage?.let { println("     Error: $it") }
                }
                println()
            }
            
            println("⚡ Performance Analysis:")
            val sortedByTime = results.testDetails.sortedByDescending { it.executionTime.toMillis() }
            println("   Slowest Tests:")
            sortedByTime.take(5).forEach { test ->
                println("   • ${test.name}: ${test.executionTime.toMillis()}ms")
            }
            println()
            
            println("🎯 Test Categories:")
            val byCategory = results.testDetails.groupBy { 
                when {
                    it.className.contains("Execute") -> "Execute Command"
                    it.className.contains("Compile") -> "Compile Command" 
                    it.className.contains("Validate") -> "Validate Command"
                    it.className.contains("Info") -> "Info Command"
                    it.className.contains("Workflow") -> "Full Workflow"
                    else -> "Other"
                }
            }
            
            byCategory.forEach { (category, tests) ->
                val categorySuccess = tests.count { it.status == TestStatus.SUCCESSFUL }
                val categoryTotal = tests.size
                val categoryRate = if (categoryTotal > 0) (categorySuccess.toDouble() / categoryTotal) * 100 else 0.0
                println("   $category: $categorySuccess/$categoryTotal (${"%.1f".format(categoryRate)}%)")
            }
            println()
            
            if (results.isAllPassed) {
                println("🎉 All E2E tests passed! CLI is ready for production use.")
            } else {
                println("⚠️ Some tests failed. Please review and fix issues before release.")
            }
            
            println("⏰ Completed at: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}")
        }
    }
    
    /**
     * Ejecuta todos los tests E2E y genera un reporte detallado.
     */
    fun runAllTests(): TestResults {
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage("dev.rubentxu.hodei.pipelines.dsl.cli.e2e"))
            .build()
        
        val launcher = LauncherFactory.create()
        val listener = E2ETestExecutionListener()
        
        launcher.execute(request, listener)
        
        return listener.getResults()
    }
    
    /**
     * Ejecuta tests de una categoría específica.
     */
    fun runCategoryTests(category: String): TestResults {
        val className = when (category.lowercase()) {
            "execute" -> "ExecuteCommandE2ETest"
            "compile" -> "CompileCommandE2ETest"
            "validate" -> "ValidateCommandE2ETest"
            "info" -> "InfoCommandE2ETest"
            "workflow" -> "FullWorkflowE2ETest"
            else -> throw IllegalArgumentException("Unknown category: $category")
        }
        
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass("dev.rubentxu.hodei.pipelines.dsl.cli.e2e.$className"))
            .build()
        
        val launcher = LauncherFactory.create()
        val listener = E2ETestExecutionListener()
        
        launcher.execute(request, listener)
        
        return listener.getResults()
    }
    
    companion object {
        private operator fun String.times(n: Int) = repeat(n)
        
        /**
         * Función de utilidad para ejecutar tests desde línea de comandos.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val runner = E2ETestRunner()
            
            when {
                args.isEmpty() -> {
                    println("🚀 Running all Pipeline DSL CLI E2E tests...")
                    runner.runAllTests()
                }
                args[0] == "category" && args.size > 1 -> {
                    println("🚀 Running ${args[1]} category tests...")
                    runner.runCategoryTests(args[1])
                }
                args[0] == "help" -> {
                    println("""
                    📖 E2E Test Runner Usage:
                    
                    🔧 Commands:
                    • No arguments: Run all E2E tests
                    • category <name>: Run specific category
                      - execute: Execute command tests
                      - compile: Compile command tests  
                      - validate: Validate command tests
                      - info: Info command tests
                      - workflow: Full workflow tests
                    • help: Show this help
                    
                    📋 Examples:
                    • gradle :pipeline-dsl:cli:test --tests E2ETestRunner
                    • gradle :pipeline-dsl:cli:test --tests E2ETestRunner.main[category,execute]
                    """.trimIndent())
                }
                else -> {
                    println("❌ Unknown command: ${args[0]}")
                    println("💡 Use 'help' for usage information")
                }
            }
        }
    }
}