package demo;

import dev.workfunctions.WorkFunction;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.nio.file.Files;

public abstract class Concat implements WorkFunction<Concat.In, RegularFile> {

    public record In(FileCollection parts) {
    }

    @Override
    public void run(In input, RegularFile output) throws Exception {
        StringBuilder text = new StringBuilder();
        for (File part : input.parts()) {
            text.append(Files.readString(part.toPath()));
        }
        Files.writeString(output.getAsFile().toPath(), text);
    }
}
