// Pipeline para test de seguridad (sandbox)
pipeline("Security Test Pipeline") {
    description("Pipeline para testear funcionalidad de sandbox de seguridad")
    
    environment {
        "TEST_ENV" to "e2e"
        "SECURITY_TEST" to "true"
    }
    
    stages {
        stage("Safe Operations") {
            steps {
                echo("Testing safe operations")
                sh("echo 'This is safe'")
                sh("pwd")
                sh("ls -la")
            }
        }
        
        stage("File Operations") {
            steps {
                echo("Testing file operations")
                sh("echo 'test content' > /tmp/safe-file.txt")
                sh("cat /tmp/safe-file.txt")
                sh("rm /tmp/safe-file.txt")
            }
        }
    }
    
    post {
        always {
            echo("Security test completed")
        }
    }
}