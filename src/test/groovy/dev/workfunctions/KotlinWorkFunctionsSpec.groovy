package dev.workfunctions

import org.gradle.testkit.runner.GradleRunner

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class KotlinWorkFunctionsSpec extends AbstractWorkFunctionsSpec {

    static final String GREETING_SCRIPT = '''
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

            override fun run(input: In, output: RegularFile) {
                output.asFile.writeText(input.text.asFile.readText().uppercase())
            }
        }

        val greeting = works.work("greet", Greet::class) {
            val formal = providers.gradleProperty("formal")
            bind(Greet.In::name).to(providers.gradleProperty("who"))
            bind(Greet.In::title).to(formal.map { "Dr. " })
            bind(Greet.In::punctuation).to(formal.map { "." })
        }

        works.work("shout", Shout::class) {
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
        file("build/work/shout/shout").text == "HELLO, WORLD!"

        when:
        result = succeeds("shout", "-Pwho=world")

        then:
        result.task(":greet").outcome == UP_TO_DATE
        result.task(":shout").outcome == UP_TO_DATE

        when: "the optional input and the input with a default get values"
        succeeds("shout", "-Pwho=world", "-Pformal=true")

        then:
        file("build/work/shout/shout").text == "HELLO, DR. WORLD."
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
        problem                              | binding                                               | error
        "a value of the wrong type"          | 'bind(Greet.In::name).to(42)'                         | "The value does not match the type of the input."
        "a provider of the wrong type"       | 'bind(Greet.In::name).to(providers.provider { 42 })'  | "The value does not match the type of the input."
        "a lambda as selector"               | 'bind { it.name }.to("world")'                        | "Cannot infer type for type parameter"
        "an input of another work function"  | 'bind(Shout.In::text).to(greeting)'                   | "Cannot infer type for type parameter"
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
