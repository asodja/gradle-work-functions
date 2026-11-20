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

- Every component of `In` is an input. `RegularFile`, `Directory` and `FileCollection` are file inputs. Anything else is a value.
- A component annotated with any `@Nullable` is optional. An interface `In` can give defaults with default methods.
- The output is a `Directory`, a `RegularFile`, or a record of those. Gradle assigns the location under `build/work/<name>`.
- File inputs use relative path sensitivity. Use `@Classpath`, `@CompileClasspath` or `@PathSensitive` on a component to change it.

## Register and wire work

```java
Works works = project.getExtensions().getByType(Works.class);

Provider<Directory> versionInfo = works.work("generateVersion", GenerateVersion.class, inputs -> {
    inputs.set(GenerateVersion.In::version, providers.gradleProperty("appVersion"));
    inputs.bind(GenerateVersion.In::template).to(layout.getProjectDirectory().file("version.tpl"));
});

Provider<RegularFile> checksums = works.work("checksumVersion", Checksum.class, inputs ->
    inputs.set(Checksum.In::files, versionInfo));

works.lifecycle("check", checksums);
```

- `set` binds a provider of exactly the input type.
- `bind(...).to(...)` binds a constant, a file, a file collection, or a provider of a subtype.
- Passing an output to an input adds the task dependency.
- An input is selected with a method reference. A lambda is a compile error.

## Kotlin

```kotlin
import dev.workfunctions.*

val greeting = works.work("greet", Greet::class) {
    inputs[Greet.In::name] = providers.gradleProperty("who")
}
```

The `In` type must be a Kotlin type. Kotlin cannot reference the components of a Java record.

## What the plugin checks

- A selector that is not an input, an unbound required input and a value of the wrong type fail at configuration time.
- A work function with fields is rejected.
- Extra task actions (`doFirst`, `doLast`) on a work task fail the task.

## Limits

- Outputs live in a fixed directory per work, not in an input-addressed workspace.
- No incremental work.
- Files inside nested values are rejected. Nested records are value inputs.
- All work shows up as the task type `WorkFunctionTask`.

## Build

```
./gradlew check
```
