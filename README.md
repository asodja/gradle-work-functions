# Work functions for Gradle

A prototype plugin for defining Gradle work as a function.
Inputs are the components of a record. The output is the second type argument.
The plugin registers one task per work and wires it through providers.

## Define work

```java
public abstract class GenerateVersion implements WorkFunction<GenerateVersion.In, Directory> {

    public record In(String version, RegularFile template, @Nullable String header) {}

    @Override
    public void run(In input, Directory output) throws Exception {
        String text = Files.readString(input.template().getAsFile().toPath());
        Files.writeString(output.file("version.properties").getAsFile().toPath(),
            text.replace("@VERSION@", input.version()));
    }
}
```

The same in Kotlin:

```kotlin
abstract class GenerateVersion : WorkFunction<GenerateVersion.In, Directory> {

    data class In(val version: String, val template: RegularFile, val header: String?)

    override fun run(input: In, output: Directory) {
        val text = input.template.asFile.readText()
        output.file("version.properties").asFile.writeText(text.replace("@VERSION@", input.version))
    }
}
```

- Every component of `In` is an input. `RegularFile`, `Directory` and `FileCollection` are file inputs. Anything else is a value.
- `In` is a record, an interface, or a Kotlin data class.
- A component annotated with any `@Nullable`, or with a nullable Kotlin type, is optional.
- An input that is not bound uses its default: a default method of an interface, or a default value of a Kotlin data class.
- The output is a `Directory`, a `RegularFile`, or a record or Kotlin data class of those. Gradle assigns the location under `build/work/<name>`.
- File inputs use relative path sensitivity. Use `@Classpath`, `@CompileClasspath` or `@PathSensitive` on a component to change it.

## Register and wire work

```java
WorkRegistry work = project.getExtensions().getByType(WorkRegistry.class);

Provider<Directory> versionInfo = work.register("generateVersion", GenerateVersion.class, inputs -> {
    inputs.bind(GenerateVersion.In::version).to(providers.gradleProperty("appVersion"));
    inputs.bind(GenerateVersion.In::template).to(layout.getProjectDirectory().file("version.tpl"));
});

Provider<RegularFile> checksums = work.register("checksumVersion", Checksum.class, inputs -> {
    inputs.bind(Checksum.In::files).to(versionInfo);
});

work.lifecycle("check", checksums);
```

The same in a Kotlin build script:

```kotlin
val versionInfo = work.register("generateVersion", GenerateVersion::class) {
    bind(GenerateVersion.In::version).to(providers.gradleProperty("appVersion"))
    bind(GenerateVersion.In::template).to(layout.projectDirectory.file("version.tpl"))
}

val checksums = work.register("checksumVersion", Checksum::class) {
    bind(Checksum.In::files).to(versionInfo)
}

work.lifecycle("check", checksums)
```

- `bind(...).to(...)` binds a provider, a constant, a file, or a file collection.
- Bind a provider to keep the value lazy. Nothing is read while binding.
- Passing an output to an input adds the task dependency.
- An input is selected with a method reference. A lambda is a compile error, in Java and in Kotlin.

## Several outputs

Make the output type a record with one component per output:

```java
public abstract class Report implements WorkFunction<Report.In, Report.Out> {

    public record In(FileCollection sources, String title) {}

    public record Out(@OutputName("report.txt") RegularFile report, Directory copies) {}

    @Override
    public void run(In input, Out output) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(input.title());
        for (File source : input.sources()) {
            lines.add(source.getName());
            Files.copy(source.toPath(), output.copies().file(source.getName()).getAsFile().toPath());
        }
        Files.write(output.report().getAsFile().toPath(), lines);
    }
}
```

`register` returns one provider for all outputs. A consumer picks one with `map`:

```java
Provider<Report.Out> report = work.register("report", Report.class, inputs -> {
    inputs.bind(Report.In::sources).to(project.files("a.txt", "b.txt"));
    inputs.bind(Report.In::title).to("Sources");
});

work.register("listCopies", ListFiles.class, inputs -> {
    inputs.bind(ListFiles.In::files).to(report.map(Report.Out::copies));
});
```

- Each output gets its own location: `build/work/report/report.txt` and `build/work/report/copies`.
- The consumer depends on the whole work, but only tracks the output it picked. It stays up to date when another output changes.

The same in Kotlin, with a data class as the output type:

```kotlin
abstract class Report : WorkFunction<Report.In, Report.Out> {

    data class In(val sources: FileCollection, val title: String)

    data class Out(@get:OutputName("report.txt") val report: RegularFile, val copies: Directory)

    override fun run(input: In, output: Out) {
        val lines = mutableListOf(input.title)
        input.sources.forEach { source ->
            lines += source.name
            source.copyTo(output.copies.file(source.name).asFile)
        }
        output.report.asFile.writeText(lines.joinToString("\n"))
    }
}

val report = work.register("report", Report::class) {
    bind(Report.In::sources).to(files("a.txt", "b.txt"))
    bind(Report.In::title).to("Sources")
}

work.register("listCopies", ListFiles::class) {
    bind(ListFiles.In::files).to(report.map { it.copies })
}
```

## Kotlin notes

- `bind` and `register` are members, so they need no import.
- The reference can point to a Kotlin property or to a component of a Java record.
- A lambda, a value of the wrong type and an input of another work function are compile errors.
- A Kotlin `In` can be an interface or a data class.

## What the plugin checks

- A selector that is not an input, an unbound required input and a value of the wrong type fail at configuration time.
- A work function with fields is rejected.
- Extra task actions (`doFirst`, `doLast`) on a work task fail the task.

## Limits

- Outputs live in a fixed directory per work, not in an input-addressed workspace.
- No incremental work.
- Files inside nested values are rejected. Nested records are value inputs.
- All work shows up as the task type `WorkFunctionTask`.

## Related work

[Gratatouille](https://github.com/GradleUp/gratatouille) has the same idea: a task is a function, its parameters are the inputs, and the output locations are assigned for you.
With Gratatouille you write Kotlin functions, and a KSP processor generates the tasks, the workers and the wiring code.

This plugin is similar, but uses no code generation.
Inputs are bound at runtime with method references, so work functions can be written and wired in Java as well as Kotlin.

## Build

```
./gradlew check
```
