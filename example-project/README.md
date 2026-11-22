# Example project

A small Java application that uses work functions.
It builds the plugin from this repository, so nothing has to be published first.

## What is in it

| Work | Function | Wired in | Shows |
|---|---|---|---|
| `generateVersion` | `GenerateVersion` | `VersionPlugin` (Java) | A work output used as a source directory of `compileJava`. |
| `checksumClasses` | `Checksum` | `build.gradle.kts` | A task output used as a work input, with `tasks.compileJava.flatMap { it.destinationDirectory }`. |
| `compileJava` | `CompileJava` | `build.gradle.kts` | The alternative: the same task, found as work with `work.named`. The block binds `release`. |
| `checksumClassesFromWork` | `Checksum` | `build.gradle.kts` | The output of `work.named` used as a work input. It writes the same checksums as `checksumClasses`. |
| `checksumVersion` | `Checksum` | `build.gradle.kts` | Work of `VersionPlugin`, found with `work.named`. The block binds the optional `header`. |
| `report` | `Report` | `build.gradle.kts` | Several outputs. |
| `checksumCopies` | `Checksum` | `build.gradle.kts` | One output of `report` used as a work input. |

- The work functions, `CompileJava` and `VersionPlugin` are in `build-logic/src/main/java/example/buildlogic`.
- Outputs are under `build/work/<name>`. `compileJava` keeps its own location, `build/classes/java/main`.

## Run it

Run from this directory, with the wrapper of the repository:

```
cd example-project
../gradlew run
```

It prints `Version 1.0`. The version comes from `appVersion` in `gradle.properties`.

## Things to try

| Command | What happens |
|---|---|
| `../gradlew check` | Runs all work. |
| `../gradlew check`, again | Everything is up to date. |
| `../gradlew run -PappVersion=2.0` | `generateVersion` and `compileJava` rerun. It prints `Version 2.0`. |
| `../gradlew check -PreportTitle=Todo` | `report` reruns. `checksumCopies` stays up to date, because the copies did not change. |
| `../gradlew check --configuration-cache`, then add `-PappVersion=3.0` | The configuration cache entry is reused. Only the affected work reruns. |
| `../gradlew check --build-cache`, delete `build`, run it again | Work that ran in the first build is loaded from the cache. |

## Things to break

Edit `build.gradle.kts` and run `../gradlew report`:

| Change | Result |
|---|---|
| `input(Report.In::title).bind(42)` | Script compilation error: the type does not match. |
| `input { it.title }.bind("x")` | Script compilation error: a lambda is not a selector. |
| Remove the `title` line | `Input 'title' of work 'report' is not bound.` |
| `input(CompileJava.In::release).bind(8)` in the `compileJava` block | `compileJava` warns that release 8 is obsolete. The block reached the task. |
| `work.named("compileTestJava", GenerateVersion::class)` | `Work 'compileTestJava' is not registered.` |
| `work.named("jar", CompileJava::class)` | The task `jar` is not a `JavaCompile`. |
