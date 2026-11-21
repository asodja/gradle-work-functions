import example.buildlogic.Checksum
import example.buildlogic.Report

plugins {
    application
    id("example.version")
}

application {
    mainClass = "example.app.Main"
}

val classChecksums = work.register("checksumClasses", Checksum::class) {
    bind(Checksum.In::files).to(tasks.compileJava.flatMap { it.destinationDirectory })
}

val report = work.register("report", Report::class) {
    bind(Report.In::sources).to(files("notes/a.txt", "notes/b.txt"))
    bind(Report.In::title).to(providers.gradleProperty("reportTitle").orElse("Notes"))
}

val copyChecksums = work.register("checksumCopies", Checksum::class) {
    bind(Checksum.In::files).to(report.map { it.copies })
}

work.lifecycle("check", classChecksums, copyChecksums)
