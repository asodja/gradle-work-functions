package dev.workfunctions.internal;

import dev.workfunctions.WorkFunction;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The task that runs one work function. Inputs are registered through the runtime API,
 * because each work function has its own set of inputs.
 */
@CacheableTask
public abstract class WorkFunctionTask extends DefaultTask {

    private Class<?> functionType;
    private Class<?> inputType;
    private Class<?> outputType;
    private WorkFunction<Object, Object> function;
    private Map<String, Object> bindings = Map.of();
    private final List<Provider<? extends FileSystemLocation>> outputLocations = new ArrayList<>();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract FileSystemOperations getFileOperations();

    // Nested, so that Gradle tracks the implementation of the work function.
    @Nested
    public WorkFunction<?, ?> getFunction() {
        return function;
    }

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutputFile();

    @SuppressWarnings("unchecked")
    void initialize(Class<?> functionType, Class<?> inputType, Class<?> outputType) {
        this.functionType = functionType;
        this.inputType = inputType;
        this.outputType = outputType;
        this.function = (WorkFunction<Object, Object>) getObjects().newInstance(functionType);

        OutputSchema outputs = OutputSchema.of(outputType, functionType, getName());
        String workspace = "work/" + getName();
        for (OutputSchema.Output output : outputs.outputs()) {
            if (outputs.isSingle() && output.directory()) {
                getOutputDirectory().set(getLayout().getBuildDirectory().dir(workspace));
                getOutputDirectory().disallowChanges();
                outputLocations.add(getOutputDirectory());
            } else if (outputs.isSingle()) {
                getOutputFile().set(getLayout().getBuildDirectory().file(workspace + "/" + output.fileName()));
                getOutputFile().disallowChanges();
                outputLocations.add(getOutputFile());
            } else if (output.directory()) {
                DirectoryProperty location = getObjects().directoryProperty();
                location.set(getLayout().getBuildDirectory().dir(workspace + "/" + output.fileName()));
                location.disallowChanges();
                getOutputs().dir(location).withPropertyName("out." + output.name());
                outputLocations.add(location);
            } else {
                RegularFileProperty location = getObjects().fileProperty();
                location.set(getLayout().getBuildDirectory().file(workspace + "/" + output.fileName()));
                location.disallowChanges();
                getOutputs().file(location).withPropertyName("out." + output.name());
                outputLocations.add(location);
            }
        }
    }

    void bind(InputSchema schema, Map<String, Object> bindings) {
        this.bindings = new LinkedHashMap<>(bindings);
        for (InputSchema.Component component : schema.components()) {
            Object source = bindings.get(component.name());
            String property = "in." + component.name();
            boolean optional = component.optional() || component.hasDefault();
            if (source == null) {
                if (!optional) {
                    throw new InvalidUserDataException("Input '" + component.name() + "' of work '" + getName() + "' is not bound.");
                }
                continue;
            }
            switch (component.kind()) {
                case VALUE -> {
                    Object value = source instanceof Provider<?> provider ? provider.map(new Values.ToSnapshotForm()) : Values.snapshotForm(source);
                    getInputs().property(property, value).optional(optional);
                }
                case FILE -> normalize(getInputs().file(source), component).withPropertyName(property).optional(optional);
                case DIRECTORY -> normalize(getInputs().dir(source), component).withPropertyName(property).optional(optional);
                case FILES -> normalize(getInputs().files(source), component).withPropertyName(property).optional(optional);
            }
        }
    }

    private static TaskInputFilePropertyBuilder normalize(TaskInputFilePropertyBuilder builder, InputSchema.Component component) {
        if (component.annotation(Classpath.class) != null) {
            return builder.withNormalizer(ClasspathNormalizer.class);
        }
        if (component.annotation(CompileClasspath.class) != null) {
            return builder.withNormalizer(CompileClasspathNormalizer.class);
        }
        PathSensitive sensitivity = component.annotation(PathSensitive.class);
        // Never absolute by default, so that outputs can be reused from another checkout.
        return builder.withPathSensitivity(sensitivity == null ? PathSensitivity.RELATIVE : sensitivity.value());
    }

    @TaskAction
    public void execute() throws Exception {
        if (getActions().size() != 1) {
            throw new InvalidUserDataException("Work '" + getName() + "' cannot have extra task actions. Put the logic in a work function.");
        }
        function.run(input(), output(true));
    }

    Object outputValue() {
        try {
            return output(false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create the output of work '" + getName() + "'.", e);
        }
    }

    private Object input() throws ReflectiveOperationException {
        InputSchema schema = InputSchema.of(inputType);
        Map<String, Object> values = new LinkedHashMap<>();
        for (InputSchema.Component component : schema.components()) {
            Object source = bindings.get(component.name());
            Object value = source instanceof Provider<?> provider ? provider.getOrNull() : source;
            if (value == null) {
                if (!component.optional() && !component.hasDefault()) {
                    throw new InvalidUserDataException("Input '" + component.name() + "' of work '" + getName() + "' has no value.");
                }
                continue;
            }
            if (component.type() == FileTree.class && value instanceof FileCollection files) {
                value = files.getAsFileTree();
            }
            Values.checkType(getName(), component, value);
            values.put(component.name(), value);
        }
        return schema.instantiate(values);
    }

    private Object output(boolean prepare) throws ReflectiveOperationException {
        OutputSchema schema = OutputSchema.of(outputType, functionType, getName());
        List<Object> locations = new ArrayList<>();
        for (int i = 0; i < schema.outputs().size(); i++) {
            FileSystemLocation location = outputLocations.get(i).get();
            if (prepare) {
                File file = location.getAsFile();
                getFileOperations().delete(spec -> spec.delete(file));
                (schema.outputs().get(i).directory() ? file : file.getParentFile()).mkdirs();
            }
            locations.add(location);
        }
        return schema.instantiate(locations);
    }

    // Named classes, not lambdas, so the configuration cache can store the output providers.

    static final class SingleDirectory implements Transformer<Provider<Directory>, WorkFunctionTask> {
        @Override
        public Provider<Directory> transform(WorkFunctionTask task) {
            return task.getOutputDirectory();
        }
    }

    static final class SingleFile implements Transformer<Provider<RegularFile>, WorkFunctionTask> {
        @Override
        public Provider<RegularFile> transform(WorkFunctionTask task) {
            return task.getOutputFile();
        }
    }

    static final class OutputValue implements Transformer<Object, WorkFunctionTask> {
        @Override
        public Object transform(WorkFunctionTask task) {
            return task.outputValue();
        }
    }
}
