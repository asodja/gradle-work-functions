# Work functions for Gradle

A prototype plugin for defining Gradle work as a function.
Inputs are the components of a record. The output is the second type argument.
The plugin registers one task per work and wires it through providers.

The idea is similar to [Gratatouille](https://github.com/GradleUp/gratatouille), but this plugin uses no code generation.

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

A second work function reads a directory and writes one file:

```java
public abstract class Checksum implements WorkFunction<Checksum.In, RegularFile> {

    public record In(Directory files) {}

    @Override
    public void run(In input, @OutputName("checksums.txt") RegularFile output) throws Exception {
        List<String> lines = new ArrayList<>();
        for (File file : new TreeSet<>(input.files().getAsFileTree().getFiles())) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
            lines.add(HexFormat.of().formatHex(hash) + "  " + file.getName());
        }
        Files.write(output.getAsFile().toPath(), lines);
    }
}
```

- Every component of `In` is an input. `RegularFile`, `Directory` and `FileCollection` are file inputs. Anything else is a value.
- `In` is a record, an interface, or a Kotlin data class.
- A component annotated with any `@Nullable`, or with a nullable Kotlin type, is optional.
- An input that is not bound uses its default: a default method of an interface, or a default value of a Kotlin data class.
- The output is a `Directory`, a `RegularFile`, or a record or Kotlin data class of those. Gradle assigns the location under `build/work/<name>`.
- `@OutputName` sets the file name of an output. Put it on the output parameter of `run`, or on a component when there are several outputs.
- File inputs use relative path sensitivity. Use `@Classpath`, `@CompileClasspath` or `@PathSensitive` on a component to change it.

## Register and wire work

Register each work and pass the output of one to the input of the other.

There are two forms to bind an input: `input(...).bind(...)` and `bind(...).to(...)`.
They do the same. Both exist for demonstration, to compare them. The final API should keep only one.

### With `input(...).bind(...)`

```java
WorkRegistry work = project.getExtensions().getByType(WorkRegistry.class);

Provider<Directory> versionInfo = work.register("generateVersion", GenerateVersion.class, it -> {
    it.input(GenerateVersion.In::version).bind(providers.gradleProperty("appVersion"));
    it.input(GenerateVersion.In::template).bind(layout.getProjectDirectory().file("version.tpl"));
});

Provider<RegularFile> checksums = work.register("checksumVersion", Checksum.class, it -> {
    it.input(Checksum.In::files).bind(versionInfo);
});

work.lifecycle("check", checksums);
```

The same in a Kotlin build script:

```kotlin
val versionInfo = work.register("generateVersion", GenerateVersion::class) {
    input(GenerateVersion.In::version).bind(providers.gradleProperty("appVersion"))
    input(GenerateVersion.In::template).bind(layout.projectDirectory.file("version.tpl"))
}

val checksums = work.register("checksumVersion", Checksum::class) {
    input(Checksum.In::files).bind(versionInfo)
}

work.lifecycle("check", checksums)
```

### With `bind(...).to(...)`

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

### In both forms

- An input is bound to a provider, a constant, a file, or a file collection.
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
Provider<Report.Out> report = work.register("report", Report.class, it -> {
    it.input(Report.In::sources).bind(project.files("a.txt", "b.txt"));
    it.input(Report.In::title).bind("Sources");
});

work.register("checksumCopies", Checksum.class, it -> {
    it.input(Checksum.In::files).bind(report.map(Report.Out::copies));
});
```

- Each output gets its own location: `build/work/report/report.txt` and `build/work/report/copies`.
- The consumer depends on the whole work, but only tracks the output it picked. It stays up to date when another output changes.

The same in a Kotlin build script:

```kotlin
val report = work.register("report", Report::class) {
    input(Report.In::sources).bind(files("a.txt", "b.txt"))
    input(Report.In::title).bind("Sources")
}

work.register("checksumCopies", Checksum::class) {
    input(Checksum.In::files).bind(report.map { it.copies })
}
```

## Wrap a task

Work can also be done by an existing task type, such as `JavaCompile`.
Such work has no `run`. It configures the task from its inputs and from the assigned output location:

```java
public abstract class CompileJava implements TaskBackedWork<CompileJava.In, Directory, JavaCompile> {

    public record In(FileCollection sources, @Nullable FileCollection classpath, int release) {}

    @Override
    public void configure(JavaCompile task, BoundInputs<In> input, Provider<Directory> output) {
        task.setSource(input.files(In::sources));
        task.setClasspath(input.files(In::classpath));
        task.getOptions().getRelease().set(input.provider(In::release));
        task.getDestinationDirectory().set(output);
    }
}
```

Register it with `registerTask`. The wiring is the same as for other work:

```java
Provider<Directory> classes = work.registerTask("compileGenerated", CompileJava.class, it -> {
    it.input(CompileJava.In::sources).bind(project.files(generatedSources));
    it.input(CompileJava.In::release).bind(17);
});

work.register("checksumClasses", Checksum.class, it -> {
    it.input(Checksum.In::files).bind(classes);
});
```

- `registerTask` registers a real `JavaCompile` task named `compileGenerated`. `tasks.withType(JavaCompile.class)` finds it.
- `input.provider(...)` and `input.files(...)` return what was bound. The values stay lazy.
- The output location is assigned, as for other work: `build/work/compileGenerated`.
- The inputs of the task decide caching, not `In`. An input of `In` that is not passed to the task is not tracked.
- The task stays open. Other code can still configure it with `tasks.named(...)`.
- `JavaCompile` needs the `java-base` plugin.

## Kotlin notes

- `input`, `bind` and `register` are members, so they need no import.
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

## Example project

[example-project](example-project) is a small Java application that uses the plugin.
It generates a source file, checksums the compiled classes and writes a report.

```
cd example-project
../gradlew run
```

## Build

```
./gradlew check
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
