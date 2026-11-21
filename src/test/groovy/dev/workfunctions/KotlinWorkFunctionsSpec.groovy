package dev.workfunctions

import org.gradle.testkit.runner.GradleRunner

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class KotlinWorkFunctionsSpec extends AbstractWorkFunctionsSpec {

    static final String GREETING_SCRIPT = '''
        import dev.workfunctions.OutputName
        import dev.workfunctions.WorkFunction

        plugins {
            id("dev.workfunctions")
        }

        abstract class Greet : WorkFunction<Greet.In, RegularFile> {
            interface In {
                val name: String
                val title: String?
                val punctuation: String
                    get() = "!"
            }

            override fun run(input: In, output: RegularFile) {
                output.asFile.writeText("Hello, ${input.title ?: ""}${input.name}${input.punctuation}")
            }
        }

        abstract class Shout : WorkFunction<Shout.In, RegularFile> {
            interface In {
                val text: RegularFile
            }

            override fun run(input: In, @OutputName("shout.txt") output: RegularFile) {
                output.asFile.writeText(input.text.asFile.readText().uppercase())
            }
        }

        val greeting = work.register("greet", Greet::class) {
            val formal = providers.gradleProperty("formal")
            bind(Greet.In::name).to(providers.gradleProperty("who"))
            bind(Greet.In::title).to(formal.map { "Dr. " })
            bind(Greet.In::punctuation).to(formal.map { "." })
        }

        work.register("shout", Shout::class) {
            bind(Shout.In::text).to(greeting)
        }
    '''

    def setup() {
        file("settings.gradle.kts") << 'rootProject.name = "example"'
    }

    def "binds inputs in a Kotlin script"() {
        given:
        file("build.gradle.kts") << GREETING_SCRIPT

        when:
        def result = succeeds("shout", "-Pwho=world")

        then:
        workTasks(result) == [":greet", ":shout"]
        file("build/work/greet/greet").text == "Hello, world!"
        file("build/work/shout/shout.txt").text == "HELLO, WORLD!"

        when:
        result = succeeds("shout", "-Pwho=world")

        then:
        result.task(":greet").outcome == UP_TO_DATE
        result.task(":shout").outcome == UP_TO_DATE

        when: "the optional input and the input with a default get values"
        succeeds("shout", "-Pwho=world", "-Pformal=true")

        then:
        file("build/work/shout/shout.txt").text == "HELLO, DR. WORLD."
    }

    def "binds inputs with input and set"() {
        given:
        file("build.gradle.kts") << GREETING_SCRIPT
            .replace('bind(Greet.In::name).to(providers.gradleProperty("who"))', 'input(Greet.In::name).set(providers.gradleProperty("who"))')
            .replace('bind(Greet.In::title).to(formal.map { "Dr. " })', 'input(Greet.In::title).set(null)')

        when:
        succeeds("greet", "-Pwho=world", "-Pformal=true")

        then:
        file("build/work/greet/greet").text == "Hello, world."
    }

    def "supports a data class with a nullable input and a default value"() {
        given:
        file("build.gradle.kts") << '''
            import dev.workfunctions.WorkFunction

            plugins {
                id("dev.workfunctions")
            }

            abstract class Greet : WorkFunction<Greet.In, RegularFile> {
                data class In(val name: String, val title: String?, val punctuation: String = "!")

                override fun run(input: In, output: RegularFile) {
                    output.asFile.writeText("Hello, ${input.title ?: ""}${input.name}${input.punctuation}")
                }
            }

            work.register("greet", Greet::class) {
                bind(Greet.In::name).to(providers.gradleProperty("who"))
                bind(Greet.In::punctuation).to(providers.gradleProperty("punctuation"))
            }
        '''

        when:
        succeeds("greet", "-Pwho=world")

        then:
        file("build/work/greet/greet").text == "Hello, world!"

        when:
        succeeds("greet", "-Pwho=world", "-Ppunctuation=?")

        then:
        file("build/work/greet/greet").text == "Hello, world?"
    }

    def "supports a data class with several outputs"() {
        given:
        file("build.gradle.kts") << '''
            import dev.workfunctions.OutputName
            import dev.workfunctions.WorkFunction

            plugins {
                id("dev.workfunctions")
            }

            abstract class Split : WorkFunction<Split.In, Split.Out> {
                data class In(val text: String)

                data class Out(@OutputName("first.txt") val first: RegularFile, val rest: Directory)

                override fun run(input: In, output: Out) {
                    val words = input.text.split(" ")
                    output.first.asFile.writeText(words.first())
                    words.drop(1).forEach { output.rest.file(it).asFile.writeText(it) }
                }
            }

            abstract class ListFiles : WorkFunction<ListFiles.In, RegularFile> {
                data class In(val files: Directory)

                override fun run(input: In, output: RegularFile) {
                    output.asFile.writeText(input.files.asFileTree.files.map { it.name }.sorted().joinToString(","))
                }
            }

            val split = work.register("split", Split::class) {
                bind(Split.In::text).to(providers.gradleProperty("text"))
            }

            work.register("listRest", ListFiles::class) {
                bind(ListFiles.In::files).to(split.map { it.rest })
            }
        '''

        when:
        def result = succeeds("listRest", "-Ptext=one two three")

        then:
        workTasks(result) == [":split", ":listRest"]
        file("build/work/split/first.txt").text == "one"
        file("build/work/listRest/listRest").text == "three,two"

        when: "only the first output changes"
        result = succeeds("listRest", "-Ptext=zero two three")

        then:
        result.task(":split").outcome == SUCCESS
        result.task(":listRest").outcome == UP_TO_DATE
        file("build/work/split/first.txt").text == "zero"
    }

    def "reruns work when the work function changes"() {
        given:
        file("build.gradle.kts") << GREETING_SCRIPT
        succeeds("greet", "-Pwho=world")

        when:
        file("build.gradle.kts").text = GREETING_SCRIPT.replace('"Hello, ', '"Hi, ')
        def result = succeeds("greet", "-Pwho=world")

        then:
        result.task(":greet").outcome == SUCCESS
        file("build/work/greet/greet").text == "Hi, world!"
    }

    def "the Kotlin compiler rejects #problem"() {
        given:
        file("build.gradle.kts") << GREETING_SCRIPT.replace('bind(Greet.In::name).to(providers.gradleProperty("who"))', binding)

        when:
        def result = fails("greet")

        then:
        result.output.contains("Script compilation error")
        result.output.contains(error)

        where:
        problem                             | binding                                                | error
        "a value of the wrong type"         | 'bind(Greet.In::name).to(42)'                          | "The value does not match the type of the input."
        "a provider of the wrong type"      | 'bind(Greet.In::name).to(providers.provider { 42 })'   | "The value does not match the type of the input."
        "a lambda as selector"              | 'bind { it.name }.to("world")'                         | "Cannot infer type for type parameter"
        "an input of another work function" | 'bind(Shout.In::text).to(greeting)'                    | "Cannot infer type for type parameter"
        "a wrong type with set"             | 'input(Greet.In::name).set(42)'                        | "Argument type mismatch"
        "a wrong provider with set"         | 'input(Greet.In::name).set(providers.provider { 42 })' | "Argument type mismatch"
        "null for a required input"         | 'input(Greet.In::name).set(null)'                      | "Null cannot be a value of a non-null type"
        "a lambda with input"               | 'input { it.name }.set("world")'                       | "Cannot infer type for type parameter"
    }

    def "rejects a reference that is not an input"() {
        given:
        file("build.gradle.kts") << GREETING_SCRIPT.replace('bind(Greet.In::name).to(providers.gradleProperty("who"))', 'bind(Greet.In::toString).to("world")')

        when:
        def result = fails("greet")

        then:
        result.output.contains("toString is not an input of")
    }

    @Override
    GradleRunner runner(String... arguments) {
        super.runner(arguments).withPluginClasspath()
    }
}
