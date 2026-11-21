package example.buildlogic;

import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.nio.file.Files;
import java.nio.file.Path;

public abstract class GenerateVersion implements WorkFunction<GenerateVersion.In, Directory> {

    public record In(String version, RegularFile template) {}

    @Override
    public void run(In input, Directory output) throws Exception {
        String text = Files.readString(input.template().getAsFile().toPath());
        Path source = output.file("example/app/Version.java").getAsFile().toPath();
        Files.createDirectories(source.getParent());
        Files.writeString(source, text.replace("@VERSION@", input.version()));
    }
}
