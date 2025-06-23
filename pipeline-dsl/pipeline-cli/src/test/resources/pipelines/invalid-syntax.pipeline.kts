// Pipeline con sintaxis inválida para test de validación
pipeline("Invalid Syntax Pipeline") {
    description("Pipeline con errores de sintaxis intencionalmente")
    
    stages {
        stage("Bad Stage") {
            steps {
                // Error de sintaxis intencional
                echo("This has invalid syntax: ${unknown_variable}")
                sh("exit 1"
                // Paréntesis sin cerrar
            }
        }
    }
    // Falta cerrar llaves