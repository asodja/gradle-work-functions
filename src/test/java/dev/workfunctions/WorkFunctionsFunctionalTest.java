package dev.workfunctions;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkFunctionsFunctionalTest {

    private static final List<String> ALL_WORK = List.of(":generateVersion", ":checksumVersion", ":report", ":archive", ":concat");

    @TempDir
    Path temp;

    @Test
    void runsWorkInDependencyOrder() throws IOException {
        Path project = javaProject("project");

        BuildResult result = run(project, "verifyAll", "-PappVersion=1.0");

        assertOutcomes(result, TaskOutcome.SUCCESS, ALL_WORK);
        assertEquals("version=1.0\n", read(project, "build/work/generateVersion/version.properties"));
        assertTrue(read(project, "build/work/checksumVersion/checksums.txt").endsWith("  version.properties\n"));
        assertEquals("Report: 1 file(s), tags [demo, release]\n", read(project, "build/work/report/summary.txt"));
        assertEquals("0.txt\n", read(project, "build/work/archive/details.list"));
        assertEquals("first\nsecond\n", read(project, "build/work/concat/concat"));
    }

    @Test
    void isUpToDateWhenNothingChanged() throws IOException {
        Path project = javaProject("project");
        run(project, "verifyAll", "-PappVersion=1.0");

        BuildResult result = run(project, "verifyAll", "-PappVersion=1.0");

        assertOutcomes(result, TaskOutcome.UP_TO_DATE, ALL_WORK);
    }

    @Test
    void rerunsOnlyWorkWhoseInputsChanged() throws IOException {
        Path project = javaProject("project");
        run(project, "verifyAll", "-PappVersion=1.0");

        BuildResult valueChanged = run(project, "verifyAll", "-PappVersion=2.0");

        assertOutcomes(valueChanged, TaskOutcome.SUCCESS, List.of(":generateVersion", ":checksumVersion", ":report", ":archive"));
        assertOutcomes(valueChanged, TaskOutcome.UP_TO_DATE, List.of(":concat"));
        assertEquals("version=2.0\n", read(project, "build/work/generateVersion/version.properties"));

        BuildResult nestedValueChanged = run(project, "verifyAll", "-PappVersion=2.0", "-Ptitle=Summary");

        assertOutcomes(nestedValueChanged, TaskOutcome.SUCCESS, List.of(":report"));
        // The report reran, but its details output is the same, so the archive has nothing to do.
        assertOutcomes(nestedValueChanged, TaskOutcome.UP_TO_DATE, List.of(":generateVersion", ":checksumVersion", ":archive"));
        assertEquals("Summary: 1 file(s), tags [demo, release]\n", read(project, "build/work/report/summary.txt"));

        Files.writeString(project.resolve("version.tpl"), "release=@VERSION@\n");
        BuildResult fileChanged = run(project, "verifyAll", "-PappVersion=2.0", "-Ptitle=Summary");

        assertOutcomes(fileChanged, TaskOutcome.SUCCESS, List.of(":generateVersion"));
        assertEquals("release=2.0\n", read(project, "build/work/generateVersion/version.properties"));
    }

    @Test
    void optionalInputCanBeBound() throws IOException {
        Path project = javaProject("project");

        run(project, "generateVersion", "-PappVersion=1.0", "-Pscenario=header");

        assertEquals("# generated\nversion=1.0\n", read(project, "build/work/generateVersion/version.properties"));
    }

    @Test
    void loadsOutputsFromTheBuildCacheInAnotherLocation() throws IOException {
        String cache = "-PcacheDir=" + temp.resolve("build-cache");
        Path first = javaProject("first");
        Path second = javaProject("second");
        run(first, "verifyAll", "-PappVersion=1.0", "--build-cache", cache);

        BuildResult result = run(second, "verifyAll", "-PappVersion=1.0", "--build-cache", cache);

        assertOutcomes(result, TaskOutcome.FROM_CACHE, ALL_WORK);
        assertEquals("version=1.0\n", read(second, "build/work/generateVersion/version.properties"));
    }

    @Test
    void reusesTheConfigurationCacheWhenALazyInputChanges() throws IOException {
        Path project = javaProject("project");
        run(project, "verifyAll", "-PappVersion=1.0", "--configuration-cache");

        BuildResult unchanged = run(project, "verifyAll", "-PappVersion=1.0", "--configuration-cache");

        assertTrue(unchanged.getOutput().contains("Reusing configuration cache."), unchanged.getOutput());
        assertOutcomes(unchanged, TaskOutcome.UP_TO_DATE, ALL_WORK);

        BuildResult changed = run(project, "verifyAll", "-PappVersion=2.0", "--configuration-cache");

        assertTrue(changed.getOutput().contains("Reusing configuration cache."), changed.getOutput());
        assertOutcomes(changed, TaskOutcome.SUCCESS, List.of(":generateVersion"));
        assertEquals("version=2.0\n", read(project, "build/work/generateVersion/version.properties"));
    }

    @Test
    void failsWhenARequiredInputHasNoValue() throws IOException {
        Path project = javaProject("project");

        BuildResult result = runner(project, "generateVersion").buildAndFail();

        assertTrue(result.getOutput().contains("in.version"), result.getOutput());
    }

    @Test
    void rejectsAStaticHelperAsSelector() throws IOException {
        Path project = javaProject("project");

        BuildResult result = runner(project, "generateVersion", "-Pscenario=staticHelper").buildAndFail();

        assertTrue(result.getOutput().contains("demo.Helper::versionOf is not an input of demo.GenerateVersion$In"), result.getOutput());
    }

    @Test
    void rejectsAMethodThatIsNotAnInput() throws IOException {
        Path project = javaProject("project");

        BuildResult result = runner(project, "generateVersion", "-Pscenario=notAnInput").buildAndFail();

        assertTrue(result.getOutput().contains("::toString is not an input of demo.GenerateVersion$In"), result.getOutput());
    }

    @Test
    void rejectsAnUnboundRequiredInput() throws IOException {
        Path project = javaProject("project");

        BuildResult result = runner(project, "unbound", "-Pscenario=unbound").buildAndFail();

        assertTrue(result.getOutput().contains("Input 'files' of work 'unbound' is not bound."), result.getOutput());
    }

    @Test
    void rejectsAStatefulWorkFunction() throws IOException {
        Path project = javaProject("project");

        BuildResult result = runner(project, "help", "-Pscenario=stateful").buildAndFail();

        assertTrue(result.getOutput().contains("Work function demo.Stateful must be stateless, but has the field 'runs'."), result.getOutput());
    }

    @Test
    void rejectsExtraTaskActions() throws IOException {
        Path project = javaProject("project");
        Files.writeString(project.resolve("build.gradle"), "\ntasks.named('generateVersion') { doLast { println 'extra' } }\n", StandardOpenOption.APPEND);

        BuildResult result = runner(project, "generateVersion", "-PappVersion=1.0").buildAndFail();

        assertTrue(result.getOutput().contains("Work 'generateVersion' cannot have extra task actions."), result.getOutput());
    }

    @Test
    void worksFromAKotlinScript() throws IOException {
        Path project = copyFixture("kotlin-script", "project");

        BuildResult first = kotlinRunner(project, "shout", "-Pwho=world").build();

        assertEquals(TaskOutcome.SUCCESS, first.task(":greet").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":shout").getOutcome());
        assertEquals("Hello, world!\n", read(project, "build/work/greet/greet"));
        assertEquals("HELLO, WORLD!\n", read(project, "build/work/shout/shout"));

        BuildResult second = kotlinRunner(project, "shout", "-Pwho=world").build();

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":greet").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":shout").getOutcome());

        kotlinRunner(project, "shout", "-Pwho=world", "-Pformal=true").build();

        assertEquals("HELLO, DR. WORLD.\n", read(project, "build/work/shout/shout"));
    }

    @Test
    void rerunsWhenTheWorkFunctionChanges() throws IOException {
        Path project = copyFixture("kotlin-script", "project");
        kotlinRunner(project, "greet", "-Pwho=world").build();
        Path script = project.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script).replace("\"Hello, ", "\"Hi, "));

        BuildResult result = kotlinRunner(project, "greet", "-Pwho=world").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":greet").getOutcome());
        assertEquals("Hi, world!\n", read(project, "build/work/greet/greet"));
    }

    @Test
    void kotlinRejectsAValueOfTheWrongType() throws IOException {
        Path project = copyFixture("kotlin-script", "project");
        Path script = project.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script).replace("providers.gradleProperty(\"who\")", "42"));

        BuildResult result = kotlinRunner(project, "greet").buildAndFail();

        assertTrue(result.getOutput().contains("Script compilation error"), result.getOutput());
    }

    private BuildResult run(Path project, String... arguments) {
        return runner(project, arguments).build();
    }

    private GradleRunner runner(Path project, String... arguments) {
        List<String> all = new ArrayList<>(Arrays.asList(arguments));
        all.add("--stacktrace");
        all.add("--warning-mode=fail");
        return GradleRunner.create()
            .withProjectDir(project.toFile())
            .withTestKitDir(new File(System.getProperty("testKitDir")))
            .withArguments(all);
    }

    private GradleRunner kotlinRunner(Path project, String... arguments) {
        return runner(project, arguments).withPluginClasspath();
    }

    // The work functions live in buildSrc, so the plugin must come from the buildSrc classpath too.
    // Otherwise the plugin API would be loaded twice, by two class loaders.
    private Path javaProject(String name) throws IOException {
        Path project = copyFixture("java-plugin", name);
        Files.write(project.resolve("buildSrc/plugin-classpath.txt"), pluginClasspath());
        return project;
    }

    private Path copyFixture(String fixture, String name) throws IOException {
        Path source;
        try {
            source = Path.of(getClass().getResource("/fixtures/" + fixture).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        Path target = temp.resolve(name);
        try (Stream<Path> files = Files.walk(source)) {
            files.forEach(file -> {
                try {
                    Path destination = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(file, destination);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return target;
    }

    private static List<String> pluginClasspath() throws IOException {
        Properties metadata = new Properties();
        try (InputStream input = WorkFunctionsFunctionalTest.class.getResourceAsStream("/plugin-under-test-metadata.properties")) {
            metadata.load(input);
        }
        return Arrays.asList(metadata.getProperty("implementation-classpath").split(File.pathSeparator));
    }

    private static void assertOutcomes(BuildResult result, TaskOutcome expected, List<String> tasks) {
        for (String task : tasks) {
            assertFalse(result.task(task) == null, task + " did not run:\n" + result.getOutput());
            assertEquals(expected, result.task(task).getOutcome(), task + "\n" + result.getOutput());
        }
    }

    private static String read(Path project, String path) throws IOException {
        return Files.readString(project.resolve(path));
    }
}
