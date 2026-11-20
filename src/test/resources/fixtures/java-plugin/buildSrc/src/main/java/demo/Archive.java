package demo;

import dev.workfunctions.OutputName;
import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OutputName("details.list")
public abstract class Archive implements WorkFunction<Archive.In, RegularFile> {

    public record In(Directory details) {
    }

    @Override
    public void run(In input, RegularFile output) throws Exception {
        try (Stream<Path> files = Files.list(input.details().getAsFile().toPath())) {
            String names = files.map(file -> file.getFileName().toString()).sorted().collect(Collectors.joining("\n", "", "\n"));
            Files.writeString(output.getAsFile().toPath(), names);
        }
    }
}
