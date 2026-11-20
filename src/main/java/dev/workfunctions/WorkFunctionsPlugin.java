package dev.workfunctions;

import dev.workfunctions.internal.DefaultWorks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public abstract class WorkFunctionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create(Works.class, "works", DefaultWorks.class, project);
    }
}
