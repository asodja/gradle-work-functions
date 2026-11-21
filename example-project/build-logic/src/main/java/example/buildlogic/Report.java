package example.buildlogic;

import dev.workfunctions.OutputName;
import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
