package dev.workfunctions

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

abstract class AbstractWorkFunctionsSpec extends Specification {

    @TempDir
    File temp

    File projectDir

    def setup() {
        projectDir = new File(temp, "project")
    }

    File file(String path) {
        def file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file
    }

    BuildResult succeeds(String... arguments) {
        runner(arguments).build()
    }

    BuildResult fails(String... arguments) {
        runner(arguments).buildAndFail()
    }

    GradleRunner runner(String... arguments) {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(new File(System.getProperty("testKitDir")))
            .withArguments(arguments.toList() + ["--stacktrace", "--warning-mode=fail"])
    }

    static List<String> workTasks(BuildResult result) {
        result.tasks*.path.findAll { !it.startsWith(":buildSrc") }
    }

    static List<String> pluginClasspath() {
        def metadata = new Properties()
        AbstractWorkFunctionsSpec.getResourceAsStream("/plugin-under-test-metadata.properties").withCloseable { metadata.load(it) }
        metadata.getProperty("implementation-classpath").split(File.pathSeparator).toList()
    }
}
