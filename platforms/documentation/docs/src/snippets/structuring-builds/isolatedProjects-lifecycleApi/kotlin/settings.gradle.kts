// tag::lifecycle-before-project[]
include("sub1")
include("sub2")

val sharedGroup = "org.example.app"
val sharedVersion: String =
    layout.settingsDirectory.file("version.txt").asFile.readText()

gradle.lifecycle.beforeProject {
    apply(plugin = "base")

    repositories {
        mavenCentral()
    }

    group = sharedGroup
    version = sharedVersion
}
// end::lifecycle-before-project[]
