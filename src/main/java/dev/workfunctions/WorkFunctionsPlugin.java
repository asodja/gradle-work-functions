package dev.workfunctions;

import dev.workfunctions.internal.DefaultWorkRegistry;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public abstract class WorkFunctionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create(WorkRegistry.class, "work", DefaultWorkRegistry.class, project);
    }
}
