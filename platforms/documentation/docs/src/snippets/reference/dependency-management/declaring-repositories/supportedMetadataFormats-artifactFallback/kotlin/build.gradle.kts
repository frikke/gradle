plugins {
    id("java")
}

// tag::artifact-fallback[]
repositories {
    maven {
        url = uri("https://repo.example.com/maven")
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}
// end::artifact-fallback[]
