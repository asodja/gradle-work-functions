package example.buildlogic;

import dev.workfunctions.TaskBackedWork;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

public abstract class CompileJava implements TaskBackedWork<Directory, JavaCompile> {

    @Override
    public Provider<Directory> output(JavaCompile task) {
        return task.getDestinationDirectory();
    }
}
