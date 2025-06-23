import dev.rubentxu.hodei.pipelines.dsl.core.dsl.pipeline

pipeline {
    stage("CLI Test") {
        steps {
            sh("echo 'Hello from CLI'")
        }
    }
}
