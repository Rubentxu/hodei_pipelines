package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import org.junit.platform.suite.api.SuiteDisplayName

/**
 * Test Suite comprehensivo para todos los tests end-to-end del CLI Pipeline DSL.
 * 
 * Este test suite ejecuta todos los tests E2E y proporciona un reporte completo
 * de la funcionalidad del CLI en escenarios realistas de uso.
 * 
 * Cómo ejecutar:
 * ```bash
 * gradle :pipeline-dsl:cli:test --tests "*E2ETestSuite"
 * ```
 * 
 * O para ejecutar todos los tests E2E:
 * ```bash
 * gradle :pipeline-dsl:cli:test --tests "*E2ETest*"
 * ```
 */
@Suite
@SuiteDisplayName("Pipeline DSL CLI End-to-End Test Suite")
@SelectClasses(
    ExecuteCommandE2ETest::class,
    CompileCommandE2ETest::class,
    ValidateCommandE2ETest::class,
    InfoCommandE2ETest::class,
    FullWorkflowE2ETest::class
)
class PipelineDslCliE2ETestSuite {
    
    companion object {
        /**
         * Información sobre la cobertura de tests E2E.
         */
        const val TEST_COVERAGE_INFO = """
        📊 Pipeline DSL CLI E2E Test Coverage:
        
        🔧 Execute Command Tests:
        ✅ Successful pipeline execution
        ✅ Failed pipeline execution with error handling
        ✅ Parallel stages execution
        ✅ Security/sandbox constraints
        ✅ Custom job-id and worker-id
        ✅ Non-existent pipeline handling
        ✅ Syntax error handling
        ✅ Verbose vs minimal output
        ✅ Timeout handling
        ✅ Execution consistency
        
        🏗️ Compile Command Tests:
        ✅ Simple pipeline compilation
        ✅ Parallel stages compilation
        ✅ Verbose detailed analysis
        ✅ Invalid syntax error handling
        ✅ Non-existent file handling
        ✅ Empty pipeline handling
        ✅ Large pipeline performance
        ✅ Complex syntax compilation
        ✅ Multiple pipeline consistency
        
        🔍 Validate Command Tests:
        ✅ Successful validation
        ✅ Invalid syntax detection
        ✅ Non-existent file handling
        ✅ Empty pipeline validation
        ✅ Semantic vs syntactic errors
        ✅ Complex syntax validation
        ✅ Multiple pipeline handling
        ✅ Performance vs compile comparison
        ✅ Large pipeline performance
        
        📖 Info Command Tests:
        ✅ DSL information display
        ✅ Supported step types listing
        ✅ Available imports display
        ✅ Features showcase
        ✅ Quick execution
        ✅ No file requirements
        ✅ Version information
        ✅ Proper formatting
        ✅ Complete capabilities
        ✅ Consistency across runs
        ✅ Comprehensive help
        
        🔄 Full Workflow Tests:
        ✅ Complete development workflow
        ✅ Error handling workflow
        ✅ Performance workflow
        ✅ Parallel workflow demonstration
        ✅ Info command integration
        
        🎯 Test Scenarios Covered:
        • Basic functionality validation
        • Error handling and edge cases
        • Performance and scalability
        • Real-world usage patterns
        • Integration between commands
        • Security and sandbox features
        • Parallel execution capabilities
        • Complex pipeline scenarios
        • User experience and output quality
        • Cross-platform compatibility considerations
        
        📋 Pipeline Types Tested:
        • Simple success pipelines
        • Intentional failure pipelines
        • Parallel execution pipelines
        • Security-constrained pipelines
        • Invalid syntax pipelines
        • Empty pipelines
        • Large/complex pipelines
        • Real-world workflow pipelines
        
        🚀 Quality Assurance Areas:
        • Command-line interface usability
        • Output formatting and clarity
        • Error message helpfulness
        • Performance benchmarks
        • Consistency and reliability
        • Integration completeness
        • Documentation accuracy
        • Edge case handling
        """
        
        /**
         * Requisitos para ejecutar los tests E2E.
         */
        const val REQUIREMENTS = """
        📋 Requirements for E2E Tests:
        
        ✅ Build Requirements:
        • CLI JAR must be built: gradle :pipeline-dsl:cli:build
        • Core module must be compiled
        • All dependencies resolved
        
        ✅ System Requirements:
        • Java 17+ runtime
        • Shell access (bash/cmd)
        • File system write permissions
        • Network access (for some tests)
        
        ✅ Test Environment:
        • Temporary directories creation
        • Command execution capabilities
        • Output capture functionality
        • Process timeout handling
        
        ⚠️ Platform Considerations:
        • Some parallel tests disabled on Windows
        • Shell commands adapted per OS
        • Path separators handled correctly
        • Timeout values adjusted for CI/CD
        """
        
        /**
         * Guía de troubleshooting para tests E2E.
         */
        const val TROUBLESHOOTING = """
        🔧 E2E Tests Troubleshooting:
        
        🚨 Common Issues:
        
        1. "CLI JAR not found"
           Solution: Run `gradle :pipeline-dsl:cli:build` first
           
        2. "Command timed out"
           Solution: Increase timeout or check system performance
           
        3. "Permission denied"
           Solution: Ensure temp directory write permissions
           
        4. "Tests fail on Windows"
           Solution: Some parallel tests are disabled on Windows
           
        5. "Inconsistent test results"
           Solution: Check for resource cleanup between tests
        
        🔍 Debug Steps:
        1. Verify CLI JAR exists in build/libs/
        2. Test CLI manually: java -jar cli.jar info
        3. Check temp directory permissions
        4. Review test output for specific errors
        5. Run individual test classes for isolation
        
        📊 Performance Guidelines:
        • Validation: < 3 seconds
        • Compilation: < 10 seconds  
        • Simple execution: < 10 seconds
        • Complex execution: < 60 seconds
        • Info command: < 2 seconds
        """
    }
}