package demo;

import dev.workfunctions.WorkFunction;
import org.gradle.api.file.RegularFile;

public abstract class Stateful implements WorkFunction<Stateful.In, RegularFile> {

    public record In(String value) {
    }

    private int runs;

    @Override
    public void run(In input, RegularFile output) {
        runs++;
    }
}
