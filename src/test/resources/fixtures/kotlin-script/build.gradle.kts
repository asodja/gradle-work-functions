import dev.workfunctions.WorkFunction
import dev.workfunctions.inputs
import dev.workfunctions.set
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
        output.asFile.writeText("Hello, ${input.title ?: ""}${input.name}${input.punctuation}\n")
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
    inputs[Greet.In::name] = providers.gradleProperty("who")
    if (providers.gradleProperty("formal").isPresent) {
        inputs[Greet.In::title] = "Dr. "
        inputs[Greet.In::punctuation] = "."
    }
}

works.work("shout", Shout::class) {
    inputs[Shout.In::text] = greeting
}
