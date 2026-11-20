package demo;

import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.nio.charset.Charset;
import java.nio.file.Files;

public abstract class GenerateVersion implements WorkFunction<GenerateVersion.In, Directory> {

    public record In(String version, RegularFile template, String encoding, @Nullable String header) {
    }

    @Override
    public void run(In input, Directory output) throws Exception {
        Charset charset = Charset.forName(input.encoding());
        String text = Files.readString(input.template().getAsFile().toPath(), charset).replace("@VERSION@", input.version());
        if (input.header() != null) {
            text = input.header() + "\n" + text;
        }
        Files.writeString(output.file("version.properties").getAsFile().toPath(), text, charset);
    }
}
