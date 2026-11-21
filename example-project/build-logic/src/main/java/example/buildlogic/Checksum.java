package example.buildlogic;

import dev.workfunctions.OutputName;
import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;

public abstract class Checksum implements WorkFunction<Checksum.In, RegularFile> {

    public record In(Directory files) {}

    @Override
    public void run(In input, @OutputName("checksums.txt") RegularFile output) throws Exception {
        List<String> lines = new ArrayList<>();
        for (File file : new TreeSet<>(input.files().getAsFileTree().getFiles())) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
            lines.add(HexFormat.of().formatHex(hash) + "  " + file.getName());
        }
        Files.write(output.getAsFile().toPath(), lines);
    }
}
