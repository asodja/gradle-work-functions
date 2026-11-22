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

## Find existing work

`named` returns the output of work that is already registered, for example by another plugin:

```java
Provider<Directory> versionInfo = work.named("generateVersion", GenerateVersion.class);
```

```kotlin
val versionInfo = work.named("generateVersion", GenerateVersion::class)
```

With a block, `named` also binds inputs:

```java
Provider<Directory> versionInfo = work.named("generateVersion", GenerateVersion.class, it -> {
    it.input(GenerateVersion.In::header).bind("Generated file");
});
```

```kotlin
val versionInfo = work.named("generateVersion", GenerateVersion::class) {
    input(GenerateVersion.In::header).bind("Generated file")
}
```

- `named` does not create or configure anything. The block runs when the work is configured.
- The block runs after the block of `register`. A binding in it replaces an earlier one.
- `register` can leave a required input unbound, if a `named` block binds it.
- The work must be registered before the call, as with `tasks.named(...)`.
- Once the work is configured, a new block is an error.

## Use an existing task as work

A task that a plugin registers can be used as work too.
Describe it with a `TaskBackedWork`. It has no `run`. It says how the inputs reach the task, and what the output of the task is:

```java
public abstract class CompileJava implements TaskBackedWork<CompileJava.In, Directory, JavaCompile> {

    public record In(FileCollection sources, FileCollection classpath, int release) {}

    @Override
    public void configure(JavaCompile task, BoundInputs<In> input) {
        input.files(In::sources, task::setSource);
        input.files(In::classpath, task::setClasspath);
        input.value(In::release, task.getOptions().getRelease()::set);
    }

    @Override
    public Provider<Directory> output(JavaCompile task) {
        return task.getDestinationDirectory();
    }
}
```

Find the task with `named`, bind what should change, and wire the output like any other:

```java
Provider<Directory> classes = work.named("compileJava", CompileJava.class, it -> {
    it.input(CompileJava.In::release).bind(17);
});

work.register("checksumClasses", Checksum.class, it -> {
    it.input(Checksum.In::files).bind(classes);
});
```

The same in a Kotlin build script:

```kotlin
val classes = work.named("compileJava", CompileJava::class) {
    input(CompileJava.In::release).bind(17)
}

work.register("checksumClasses", Checksum::class) {
    input(Checksum.In::files).bind(classes)
}
```

- The block is optional. `work.named("compileJava", CompileJava.class)` only returns the output.
- Only bound inputs reach the task. `input.value(...)` and `input.files(...)` do nothing for an input that is not bound.
- `input.value(...)` passes a provider, so the value stays lazy. `input.files(...)` passes a file collection that keeps task dependencies.
- A task keeps its own output location, such as `build/classes/java/main`.
- The plugin that registered the task still owns it. The block competes with that plugin, as `tasks.named(...) { }` does. The task decides caching.
- A task cannot be registered through `work`. Register it as usual, or let a plugin do it, then find it.

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
- A `TaskBackedWork` must be written in Java. `BoundInputs` has no Kotlin form yet.

## Example project

[example-project](example-project) is a small Java application that uses the plugin.
It generates a source file, finds `compileJava` as work, checksums the compiled classes and writes a report.

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
