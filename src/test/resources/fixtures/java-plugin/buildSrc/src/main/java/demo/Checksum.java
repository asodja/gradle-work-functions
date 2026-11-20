package demo;

import dev.workfunctions.OutputName;
import dev.workfunctions.WorkFunction;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

@OutputName("checksums.txt")
public abstract class Checksum implements WorkFunction<Checksum.In, RegularFile> {

    public record In(Directory files) {
    }

    @Override
    public void run(In input, RegularFile output) throws Exception {
        Path root = input.files().getAsFile().toPath();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        StringBuilder lines = new StringBuilder();
        for (Path file : files) {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            lines.append(HexFormat.of().formatHex(hash)).append("  ").append(root.relativize(file)).append('\n');
        }
        Files.writeString(output.getAsFile().toPath(), lines);
    }
}
