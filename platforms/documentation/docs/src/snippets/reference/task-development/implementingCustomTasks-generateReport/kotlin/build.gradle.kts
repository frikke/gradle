// tag::register-task[]
tasks.register<GenerateReportTask>("generateReport") {
    sourceDirectory = layout.projectDirectory.dir("src/main")
    reportFile = layout.buildDirectory.file("reports/directoryReport.txt")
}

tasks.build {
    dependsOn("generateReport")
}
// end::register-task[]
