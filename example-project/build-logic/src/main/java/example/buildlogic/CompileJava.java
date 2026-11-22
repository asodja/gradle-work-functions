package example.buildlogic;

import dev.workfunctions.BoundInputs;
import dev.workfunctions.TaskBackedWork;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

public abstract class CompileJava implements TaskBackedWork<CompileJava.In, Directory, JavaCompile> {

    public record In(FileCollection sources, FileCollection classpath, int release) {}

    @Override
    public void configure(JavaCompile task, BoundInputs<In> input) {
        input.files(In::sources, task::setSource);
        input.files(In::classpath, task::setClasspath);
        input.value(In::release, task.getOptions().getRelease()::set);
    }

    @Override
    public Provider<Directory> output(JavaCompile task) {
        return task.getDestinationDirectory();
    }
}
