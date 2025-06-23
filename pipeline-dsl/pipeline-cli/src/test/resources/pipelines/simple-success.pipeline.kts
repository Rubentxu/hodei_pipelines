// Pipeline simple que siempre tiene éxito
pipeline("Simple Success Pipeline") {
    description("Pipeline básico para tests que siempre tiene éxito")
    
    environment {
        "TEST_ENV" to "e2e"
        "PIPELINE_TYPE" to "success"
    }
    
    stages {
        stage("Echo Test") {
            steps {
                echo("Hello from CLI test!")
                echo("Environment: \$TEST_ENV")
                echo("Pipeline type: \$PIPELINE_TYPE")
            }
        }
        
        stage("Command Test") {
            steps {
                sh("echo 'Shell command executed'")
                sh("date")
            }
        }
    }
    
    post {
        always {
            echo("Pipeline completed - always runs")
        }
        success {
            echo("Pipeline succeeded!")
        }
    }
}