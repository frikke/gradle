plugins {
    id("java")
}

abstract class MyCustomTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
}

val myTask = tasks.register<MyCustomTask>("myTask").get()

// tag::eager-evaluation[]
val version = project.version.toString()         // evaluated now
val file = File("build/output.txt")              // evaluated now
myTask.outputFile.set(file)
// end::eager-evaluation[]

// tag::lazy-evaluation[]
val outputFile: RegularFileProperty = project.objects.fileProperty()
outputFile.set(layout.buildDirectory.file("output.txt"))    // evaluated later
// end::lazy-evaluation[]

// tag::eager-task[]
// Not Configuration Cache compatible
tasks.register("printVersion") {
    doLast {
        val version = project.version.toString()
        println("Version is $version")
    }
}
// end::eager-task[]

// tag::lazy-task[]
// Configuration Cache compatible
tasks.register("printVersionLazy") {
    val version: Property<String> = project.objects.property(String::class.java)
    version.set(project.version.toString())
    doLast {
        println("Version is ${version.get()}")
    }
}
// end::lazy-task[]
