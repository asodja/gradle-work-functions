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
    input(Report.In::sources).set(files("notes/a.txt", "notes/b.txt"))
    input(Report.In::title).set(providers.gradleProperty("reportTitle").orElse("Notes"))
}

val copyChecksums = work.register("checksumCopies", Checksum::class) {
    input(Checksum.In::files).set(report.map { it.copies })
}

work.lifecycle("check", classChecksums, copyChecksums)
