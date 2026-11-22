package example.buildlogic;

import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public abstract class GenerateVersion implements WorkFunction<GenerateVersion.In, Directory> {

    public record In(String version, RegularFile template, @Nullable String header) {}

    @Override
    public void run(In input, Directory output) throws Exception {
        String text = Files.readString(input.template().getAsFile().toPath());
        String header = input.header() == null ? "" : "// " + input.header() + "\n";
        Path source = output.file("example/app/Version.java").getAsFile().toPath();
        Files.createDirectories(source.getParent());
        Files.writeString(source, header + text.replace("@VERSION@", input.version()));
    }
}
