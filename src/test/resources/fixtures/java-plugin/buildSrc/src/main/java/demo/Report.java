package demo;

import dev.workfunctions.OutputName;
import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.nio.file.Files;
import java.util.List;

public abstract class Report implements WorkFunction<Report.In, Report.Out> {

    public record Options(boolean verbose, String title) {
    }

    public record In(RegularFile checksums, List<String> tags, Options options) {
    }

    public record Out(@OutputName("summary.txt") RegularFile summary, Directory details) {
    }

    @Override
    public void run(In input, Out output) throws Exception {
        List<String> lines = Files.readAllLines(input.checksums().getAsFile().toPath());
        String summary = input.options().title() + ": " + lines.size() + " file(s), tags " + input.tags() + "\n";
        Files.writeString(output.summary().getAsFile().toPath(), summary);
        for (int i = 0; i < lines.size(); i++) {
            Files.writeString(output.details().file(i + ".txt").getAsFile().toPath(), lines.get(i) + "\n");
        }
    }
}
