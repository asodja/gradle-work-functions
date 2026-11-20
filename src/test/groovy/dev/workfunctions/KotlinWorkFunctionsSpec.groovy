package dev.workfunctions

import org.gradle.testkit.runner.GradleRunner

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class KotlinWorkFunctionsSpec extends AbstractWorkFunctionsSpec {

    static final String GREETING_SCRIPT = '''
        import dev.workfunctions.WorkFunction
        import dev.workfunctions.bind
        import dev.workfunctions.work

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
        file("build.gradle.kts") << GREETING_SCRIPT.replace('providers.gradleProperty("who")', source)

        when:
        def result = fails("greet")

        then:
        result.output.contains("Script compilation error")
        result.output.contains("The value does not match the type of the input.")

        where:
        problem                        | source
        "a value of the wrong type"    | '42'
        "a provider of the wrong type" | 'providers.provider { 42 }'
    }

    @Override
    GradleRunner runner(String... arguments) {
        super.runner(arguments).withPluginClasspath()
    }
}
