plugins {
    id("org.springframework.boot") version "3.3.0" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.bioengine"
    version = "0.0.1-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}