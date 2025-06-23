// Pipeline que falla intencionalmente
pipeline("Simple Failure Pipeline") {
    description("Pipeline que falla para test de error handling")
    
    environment {
        "TEST_ENV" to "e2e"
        "SHOULD_FAIL" to "true"
    }
    
    stages {
        stage("Success Stage") {
            steps {
                echo("This stage should succeed")
                sh("echo 'Pre-failure step'")
            }
        }
        
        stage("Failure Stage") {
            steps {
                echo("About to fail...")
                sh("exit 1")  // Comando que falla
                echo("This should not run")
            }
        }
        
        stage("Should Not Run") {
            steps {
                echo("This stage should not run due to previous failure")
            }
        }
    }
    
    post {
        always {
            echo("Post-always: This always runs even on failure")
        }
        failure {
            echo("Post-failure: Pipeline failed as expected")
        }
    }
}