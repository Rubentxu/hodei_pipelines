import dev.rubentxu.hodei.pipelines.dsl.core.dsl.pipeline

pipeline {
    stage("Source") {
        steps {
            checkout("my-repo")
        }
    }
    stage("Build") {
        steps {
            sh("echo 'Building...'" )
        }
    }
}
