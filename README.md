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
- `In` is a record, an interface, or a Kotlin data class.
- A component annotated with any `@Nullable`, or with a nullable Kotlin type, is optional.
- An input that is not bound uses its default: a default method of an interface, or a default value of a Kotlin data class.
- The output is a `Directory`, a `RegularFile`, or a record of those. Gradle assigns the location under `build/work/<name>`.
- File inputs use relative path sensitivity. Use `@Classpath`, `@CompileClasspath` or `@PathSensitive` on a component to change it.

## Register and wire work

```java
WorkRegistry work = project.getExtensions().getByType(WorkRegistry.class);

Provider<Directory> versionInfo = work.register("generateVersion", GenerateVersion.class, inputs -> {
    inputs.bind(GenerateVersion.In::version).to(providers.gradleProperty("appVersion"));
    inputs.bind(GenerateVersion.In::template).to(layout.getProjectDirectory().file("version.tpl"));
});

Provider<RegularFile> checksums = work.register("checksumVersion", Checksum.class, inputs ->
    inputs.bind(Checksum.In::files).to(versionInfo));

work.lifecycle("check", checksums);
```

- `bind(...).to(...)` binds a provider, a constant, a file, or a file collection.
- Bind a provider to keep the value lazy. Nothing is read while binding.
- Passing an output to an input adds the task dependency.
- An input is selected with a method reference. A lambda is a compile error, in Java and in Kotlin.

## Kotlin

```kotlin
val greeting = work.register("greet", Greet::class) {
    bind(Greet.In::name).to(providers.gradleProperty("who"))
}
```

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

## Build

```
./gradlew check
```
