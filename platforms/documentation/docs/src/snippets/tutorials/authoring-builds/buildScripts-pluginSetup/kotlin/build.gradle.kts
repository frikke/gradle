plugins {
    `java-gradle-plugin`
}

// tag::update-plugin-config[]
gradlePlugin {
    val license by plugins.creating {   // Update name to license
        id = "com.tutorial.license"     // Update id to com.gradle.license
        implementationClass = "license.LicensePlugin"
    }
}
// end::update-plugin-config[]

// tag::greeting-plugin[]
class LicensePlugin: Plugin<Project> {
    override fun apply(project: Project) {                          // Apply plugin
        project.tasks.register("greeting") { task ->                // Register a task
            task.doLast {
                println("Hello from plugin 'com.tutorial.greeting'")  // Hello world printout
            }
        }
    }
}
// end::greeting-plugin[]
