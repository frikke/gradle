plugins {
    id("java")
}

abstract class LicenseTask : DefaultTask()

// tag::license-plugin[]
class LicensePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("license", LicenseTask::class.java) { task ->
            task.description = "add a license header to source code"   // Add description
            task.group = "from license plugin"                         // Add group
        }
    }
}
// end::license-plugin[]
