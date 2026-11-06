plugins {
    `java-library`
    id("com.google.protobuf")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    api("io.grpc:grpc-protobuf:1.63.0")
    api("io.grpc:grpc-stub:1.63.0")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    
    }

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}