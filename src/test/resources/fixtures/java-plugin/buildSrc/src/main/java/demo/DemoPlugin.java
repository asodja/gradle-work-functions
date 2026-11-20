package demo;

import dev.workfunctions.Works;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.List;

public abstract class DemoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("dev.workfunctions");
        Works works = project.getExtensions().getByType(Works.class);
        ProviderFactory providers = project.getProviders();
        String scenario = providers.gradleProperty("scenario").getOrElse("default");

        Provider<Directory> versionInfo = works.work("generateVersion", GenerateVersion.class, inputs -> {
            inputs.set(GenerateVersion.In::version, providers.gradleProperty("appVersion"));
            inputs.bind(GenerateVersion.In::template).to(project.getLayout().getProjectDirectory().file("version.tpl"));
            inputs.bind(GenerateVersion.In::encoding).to("UTF-8");
            if (scenario.equals("header")) {
                inputs.bind(GenerateVersion.In::header).to("# generated");
            }
            if (scenario.equals("staticHelper")) {
                inputs.bind(Helper::versionOf).to("1.0");
            }
            if (scenario.equals("notAnInput")) {
                inputs.bind(GenerateVersion.In::toString).to("1.0");
            }
        });

        Provider<RegularFile> checksums = works.work("checksumVersion", Checksum.class, inputs ->
            inputs.set(Checksum.In::files, versionInfo));

        Provider<Report.Out> report = works.work("report", Report.class, inputs -> {
            inputs.set(Report.In::checksums, checksums);
            inputs.bind(Report.In::tags).to(List.of("demo", "release"));
            inputs.set(Report.In::options, providers.gradleProperty("title").orElse("Report").map(title -> new Report.Options(false, title)));
        });

        Provider<RegularFile> archive = works.work("archive", Archive.class, inputs ->
            inputs.set(Archive.In::details, report.map(Report.Out::details)));

        Provider<RegularFile> concat = works.work("concat", Concat.class, inputs ->
            inputs.bind(Concat.In::parts).to(project.files("parts/a.txt", "parts/b.txt")));

        works.lifecycle("verifyAll", archive, concat);

        if (scenario.equals("unbound")) {
            works.work("unbound", Checksum.class, inputs -> {
            });
        }
        if (scenario.equals("stateful")) {
            works.work("stateful", Stateful.class, inputs -> inputs.bind(Stateful.In::value).to("x"));
        }
    }
}
