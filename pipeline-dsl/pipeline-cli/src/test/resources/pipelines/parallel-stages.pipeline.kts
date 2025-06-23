// Pipeline con stages paralelos
pipeline("Parallel Stages Pipeline") {
    description("Pipeline con ejecución paralela para test de concurrencia")
    
    environment {
        "TEST_ENV" to "e2e"
        "PARALLEL_TEST" to "true"
    }
    
    stages {
        stage("Setup") {
            steps {
                echo("Setting up parallel test")
                sh("mkdir -p /tmp/parallel-test")
            }
        }
        
        stage("Parallel Processing") {
            parallel {
                stage("Task A") {
                    steps {
                        echo("Starting parallel task A")
                        sh("sleep 1")
                        sh("echo 'Task A completed' > /tmp/parallel-test/task-a.txt")
                    }
                }
                
                stage("Task B") {
                    steps {
                        echo("Starting parallel task B")
                        sh("sleep 2")
                        sh("echo 'Task B completed' > /tmp/parallel-test/task-b.txt")
                    }
                }
                
                stage("Task C") {
                    steps {
                        echo("Starting parallel task C")
                        sh("sleep 1")
                        sh("echo 'Task C completed' > /tmp/parallel-test/task-c.txt")
                    }
                }
            }
        }
        
        stage("Verify Results") {
            steps {
                echo("Verifying parallel execution results")
                sh("ls -la /tmp/parallel-test/")
                sh("cat /tmp/parallel-test/task-*.txt")
                sh("rm -rf /tmp/parallel-test")
            }
        }
    }
}