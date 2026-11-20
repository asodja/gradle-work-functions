package dev.workfunctions

import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class JavaWorkFunctionsSpec extends AbstractWorkFunctionsSpec {

    def "runs work in dependency order"() {
        given:
        versionExample()

        when:
        def result = succeeds("verify", "-PappVersion=1.0")

        then:
        workTasks(result) == [":generateVersion", ":countLines", ":verify"]
        file("build/work/generateVersion/version.properties").text == "version=1.0"
        file("build/work/countLines/lines.txt").text == "lines=1"
    }

    def "is up to date when nothing changed"() {
        given:
        versionExample()
        succeeds("verify", "-PappVersion=1.0")

        when:
        def result = succeeds("verify", "-PappVersion=1.0")

        then:
        result.task(":generateVersion").outcome == UP_TO_DATE
        result.task(":countLines").outcome == UP_TO_DATE
    }

    def "reruns work when an input value changes"() {
        given:
        versionExample()
        succeeds("verify", "-PappVersion=1.0")

        when:
        def result = succeeds("verify", "-PappVersion=2.0")

        then:
        result.task(":generateVersion").outcome == SUCCESS
        result.task(":countLines").outcome == SUCCESS
        file("build/work/generateVersion/version.properties").text == "version=2.0"
    }

    def "reruns work when an input file changes"() {
        given:
        versionExample()
        succeeds("verify", "-PappVersion=1.0")

        when:
        file("version.tpl").text = "name=example\nversion=@VERSION@"
        def result = succeeds("verify", "-PappVersion=1.0")

        then:
        result.task(":generateVersion").outcome == SUCCESS
        file("build/work/countLines/lines.txt").text == "lines=2"
    }

    def "loads outputs from the build cache in another location"() {
        given:
        def cache = "-PcacheDir=${new File(temp, "build-cache")}".toString()
        projectDir = new File(temp, "first")
        versionExample()
        succeeds("verify", "-PappVersion=1.0", "--build-cache", cache)

        when:
        projectDir = new File(temp, "second")
        versionExample()
        def result = succeeds("verify", "-PappVersion=1.0", "--build-cache", cache)

        then:
        result.task(":generateVersion").outcome == FROM_CACHE
        result.task(":countLines").outcome == FROM_CACHE
        file("build/work/generateVersion/version.properties").text == "version=1.0"
    }

    def "reuses the configuration cache when a lazy input changes"() {
        given:
        versionExample()
        succeeds("verify", "-PappVersion=1.0", "--configuration-cache")

        when:
        def result = succeeds("verify", "-PappVersion=2.0", "--configuration-cache")

        then:
        result.output.contains("Reusing configuration cache.")
        result.task(":generateVersion").outcome == SUCCESS
        file("build/work/generateVersion/version.properties").text == "version=2.0"
    }

    def "supports file collections, nested values, optional inputs and several outputs"() {
        given:
        buildSrc()
        workFunction("Report", '''
            public abstract class Report implements WorkFunction<Report.In, Report.Out> {

                public record Style(String title, boolean numbered) {}

                public record In(FileCollection sources, Style style, @Nullable String footer) {}

                public record Out(@OutputName("report.txt") RegularFile report, Directory copies) {}

                @Override
                public void run(In input, Out output) throws Exception {
                    List<String> lines = new ArrayList<>();
                    lines.add(input.style().title());
                    for (File source : input.sources()) {
                        lines.add((input.style().numbered() ? lines.size() + ". " : "") + source.getName());
                        Files.copy(source.toPath(), output.copies().file(source.getName()).getAsFile().toPath());
                    }
                    if (input.footer() != null) {
                        lines.add(input.footer());
                    }
                    Files.write(output.report().getAsFile().toPath(), lines);
                }
            }
        ''')
        workFunction("ListFiles", '''
            public abstract class ListFiles implements WorkFunction<ListFiles.In, RegularFile> {

                public record In(Directory files) {}

                @Override
                public void run(In input, RegularFile output) throws Exception {
                    List<String> names = new ArrayList<>();
                    for (File file : input.files().getAsFileTree()) {
                        names.add(file.getName());
                    }
                    Collections.sort(names);
                    Files.writeString(output.getAsFile().toPath(), String.join(",", names));
                }
            }
        ''')
        plugin('''
            Provider<Report.Out> report = work.register("report", Report.class, inputs -> {
                inputs.bind(Report.In::sources).to(project.files("a.txt", "b.txt"));
                inputs.bind(Report.In::style).to(providers.gradleProperty("title").map(title -> new Report.Style(title, true)));
                inputs.bind(Report.In::footer).to(providers.gradleProperty("footer"));
            });

            work.register("listCopies", ListFiles.class, inputs ->
                inputs.bind(ListFiles.In::files).to(report.map(Report.Out::copies)));
        ''')
        file("a.txt") << "first"
        file("b.txt") << "second"

        when:
        def result = succeeds("listCopies", "-Ptitle=Sources")

        then:
        workTasks(result) == [":report", ":listCopies"]
        file("build/work/report/report.txt").readLines() == ["Sources", "1. a.txt", "2. b.txt"]
        file("build/work/report/copies/a.txt").text == "first"
        file("build/work/listCopies/listCopies").text == "a.txt,b.txt"

        when: "a nested value and an optional input change"
        result = succeeds("listCopies", "-Ptitle=Files", "-Pfooter=end")

        then: "the report reruns, but its copies are unchanged, so the consumer does not"
        result.task(":report").outcome == SUCCESS
        result.task(":listCopies").outcome == UP_TO_DATE
        file("build/work/report/report.txt").readLines() == ["Files", "1. a.txt", "2. b.txt", "end"]
    }

    def "a Kotlin script binds the inputs of a Java record"() {
        given:
        buildSrc()
        workFunction("Greet", '''
            public abstract class Greet implements WorkFunction<Greet.In, RegularFile> {

                public record In(String name, boolean loud) {}

                @Override
                public void run(In input, RegularFile output) throws Exception {
                    String text = "Hello, " + input.name();
                    Files.writeString(output.getAsFile().toPath(), input.loud() ? text.toUpperCase() : text);
                }
            }
        ''')
        file("build.gradle.kts") << '''
            import example.Greet

            plugins {
                id("dev.workfunctions")
            }

            work.register("greet", Greet::class) {
                bind(Greet.In::name).to(providers.gradleProperty("who"))
                bind(Greet.In::loud).to(true)
            }
        '''

        when:
        succeeds("greet", "-Pwho=world")

        then:
        file("build/work/greet/greet").text == "HELLO, WORLD"
    }

    def "javac rejects #problem"() {
        given:
        buildSrc()
        greetFunction()
        plugin("""
            work.register("greet", Greet.class, inputs -> $binding);
        """)

        when:
        def result = fails("greet")

        then:
        result.output.contains("Task :buildSrc:compileJava FAILED")
        result.output.contains(error)

        where:
        problem                       | binding                                         | error
        "a value of the wrong type"   | 'inputs.bind(Greet.In::name).to(42)'            | "no suitable method found for to(int)"
        "a lambda as selector"        | 'inputs.bind(input -> input.name()).to("x")'    | "invalid functional descriptor for lambda expression"
    }

    def "rejects a selector that is #problem"() {
        given:
        buildSrc()
        greetFunction()
        workFunction("Helper", '''
            public class Helper {
                public static String nameOf(Greet.In input) {
                    return input.name();
                }
            }
        ''')
        plugin("""
            work.register("greet", Greet.class, inputs -> inputs.bind($selector).to("world"));
        """)

        when:
        def result = fails("greet")

        then:
        result.output.contains(error)

        where:
        problem           | selector             | error
        "a static helper" | 'Helper::nameOf'     | 'example.Helper::nameOf is not an input of example.Greet$In'
        "not an input"    | 'Greet.In::toString' | '::toString is not an input of example.Greet$In'
    }

    def "rejects a required input that is not bound"() {
        given:
        buildSrc()
        greetFunction()
        plugin('''
            work.register("greet", Greet.class, inputs -> {});
        ''')

        when:
        def result = fails("greet")

        then:
        result.output.contains("Input 'name' of work 'greet' is not bound.")
    }

    def "fails when a required input has no value"() {
        given:
        buildSrc()
        greetFunction()
        plugin('''
            work.register("greet", Greet.class, inputs -> inputs.bind(Greet.In::name).to(providers.gradleProperty("who")));
        ''')

        when:
        def result = fails("greet")

        then:
        result.output.contains("property 'in.name' doesn't have a configured value")
    }

    def "rejects a work function with state"() {
        given:
        buildSrc()
        workFunction("Counter", '''
            public abstract class Counter implements WorkFunction<Counter.In, RegularFile> {

                public record In(String name) {}

                private int runs;

                @Override
                public void run(In input, RegularFile output) {
                    runs++;
                }
            }
        ''')
        plugin('''
            work.register("count", Counter.class, inputs -> inputs.bind(Counter.In::name).to("x"));
        ''')

        when:
        def result = fails("help")

        then:
        result.output.contains("Work function example.Counter must be stateless, but has the field 'runs'.")
    }

    def "rejects extra task actions"() {
        given:
        buildSrc()
        greetFunction()
        plugin('''
            work.register("greet", Greet.class, inputs -> inputs.bind(Greet.In::name).to("world"));
        ''')
        file("build.gradle") << '''
            tasks.named("greet") {
                doLast { println "extra" }
            }
        '''

        when:
        def result = fails("greet")

        then:
        result.output.contains("Work 'greet' cannot have extra task actions.")
    }

    private void versionExample() {
        buildSrc()
        workFunction("GenerateVersion", '''
            public abstract class GenerateVersion implements WorkFunction<GenerateVersion.In, Directory> {

                public record In(String version, RegularFile template) {}

                @Override
                public void run(In input, Directory output) throws Exception {
                    String text = Files.readString(input.template().getAsFile().toPath());
                    Files.writeString(output.file("version.properties").getAsFile().toPath(), text.replace("@VERSION@", input.version()));
                }
            }
        ''')
        workFunction("CountLines", '''
            public abstract class CountLines implements WorkFunction<CountLines.In, RegularFile> {

                public record In(Directory files) {}

                @Override
                public void run(In input, @OutputName("lines.txt") RegularFile output) throws Exception {
                    int lines = 0;
                    for (File file : input.files().getAsFileTree()) {
                        lines += Files.readAllLines(file.toPath()).size();
                    }
                    Files.writeString(output.getAsFile().toPath(), "lines=" + lines);
                }
            }
        ''')
        plugin('''
            Provider<Directory> versionInfo = work.register("generateVersion", GenerateVersion.class, inputs -> {
                inputs.bind(GenerateVersion.In::version).to(providers.gradleProperty("appVersion"));
                inputs.bind(GenerateVersion.In::template).to(layout.getProjectDirectory().file("version.tpl"));
            });

            Provider<RegularFile> lineCount = work.register("countLines", CountLines.class, inputs ->
                inputs.bind(CountLines.In::files).to(versionInfo));

            work.lifecycle("verify", lineCount);
        ''')
        file("version.tpl") << "version=@VERSION@"
    }

    private void greetFunction() {
        workFunction("Greet", '''
            public abstract class Greet implements WorkFunction<Greet.In, RegularFile> {

                public record In(String name) {}

                @Override
                public void run(In input, RegularFile output) throws Exception {
                    Files.writeString(output.getAsFile().toPath(), "Hello, " + input.name());
                }
            }
        ''')
    }

    // Work functions are compiled in buildSrc. The plugin must come from the buildSrc classpath too,
    // otherwise its API would be loaded twice, by two class loaders.
    private void buildSrc() {
        file("settings.gradle") << """
            rootProject.name = 'example'

            def cacheDir = providers.gradleProperty('cacheDir').orNull
            if (cacheDir != null) {
                buildCache {
                    local {
                        directory = new File(cacheDir)
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation gradleApi()
                implementation files(${pluginClasspath().collect { "'$it'" }.join(", ")})
            }
        """
    }

    private void workFunction(String name, String source) {
        file("buildSrc/src/main/java/example/${name}.java") << """
            package example;

            import dev.workfunctions.OutputName;
            import dev.workfunctions.WorkFunction;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.FileCollection;
            import org.gradle.api.file.RegularFile;
            import org.jspecify.annotations.Nullable;

            import java.io.File;
            import java.nio.file.Files;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;
            $source
        """
    }

    /**
     * Writes a plugin whose apply method contains the given wiring.
     * The wiring can use {@code project}, {@code work}, {@code providers} and {@code layout}.
     */
    private void plugin(String wiring) {
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/example.properties") << "implementation-class=example.ExamplePlugin"
        file("build.gradle") << """
            plugins {
                id 'example'
            }
        """
        file("buildSrc/src/main/java/example/ExamplePlugin.java") << """
            package example;

            import dev.workfunctions.WorkRegistry;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.ProjectLayout;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.provider.ProviderFactory;

            public abstract class ExamplePlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getPluginManager().apply("dev.workfunctions");
                    WorkRegistry work = project.getExtensions().getByType(WorkRegistry.class);
                    ProviderFactory providers = project.getProviders();
                    ProjectLayout layout = project.getLayout();
                    $wiring
                }
            }
        """
    }
}
