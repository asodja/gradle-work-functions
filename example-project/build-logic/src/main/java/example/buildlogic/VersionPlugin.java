package example.buildlogic;

import dev.workfunctions.WorkFunctionsPlugin;
import dev.workfunctions.WorkRegistry;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class VersionPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(WorkFunctionsPlugin.class);
        WorkRegistry work = project.getExtensions().getByType(WorkRegistry.class);

        Provider<Directory> versionSources = work.register("generateVersion", GenerateVersion.class, inputs -> {
            inputs.bind(GenerateVersion.In::version).to(project.getProviders().gradleProperty("appVersion"));
            inputs.bind(GenerateVersion.In::template).to(project.getLayout().getProjectDirectory().file("Version.java.tpl"));
        });

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava().srcDir(versionSources);
    }
}
