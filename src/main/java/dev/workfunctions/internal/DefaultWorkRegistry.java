package dev.workfunctions.internal;

import dev.workfunctions.Inputs;
import dev.workfunctions.TaskBackedWork;
import dev.workfunctions.WorkFunction;
import dev.workfunctions.WorkRegistry;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class DefaultWorkRegistry implements WorkRegistry {

    private static final String GROUP = "work";

    private final Project project;

    @Inject
    public DefaultWorkRegistry(Project project) {
        this.project = project;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Provider<O> register(String name, Class<? extends WorkFunction<I, O>> type, Action<? super Inputs<I>> configuration) {
        Class<?>[] typeArguments = TypeArguments.of(type);
        Class<?> inputType = typeArguments[0];
        Class<?> outputType = typeArguments[1];
        requireStateless(type);
        InputSchema inputSchema = InputSchema.of(inputType);
        OutputSchema outputSchema = OutputSchema.of(outputType, type, name);

        TaskProvider<WorkFunctionTask> task = project.getTasks().register(name, WorkFunctionTask.class, workTask -> {
            workTask.setGroup(GROUP);
            workTask.setDescription("Runs " + type.getSimpleName() + ".");
            workTask.initialize(type, inputType, outputType);
            DefaultInputs<I> inputs = new DefaultInputs<>(name, inputSchema);
            configuration.execute(inputs);
            workTask.bind(inputSchema, inputs.bindings());
        });

        if (!outputSchema.isSingle()) {
            return (Provider<O>) task.map(new WorkFunctionTask.OutputValue());
        }
        if (outputSchema.outputs().get(0).directory()) {
            return (Provider<O>) task.flatMap(new WorkFunctionTask.SingleDirectory());
        }
        return (Provider<O>) task.flatMap(new WorkFunctionTask.SingleFile());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O, T extends Task> Provider<O> registerTask(String name, Class<? extends TaskBackedWork<I, O, T>> type, Action<? super Inputs<I>> configuration) {
        Class<?>[] typeArguments = TypeArguments.of(type, TaskBackedWork.class);
        Class<?> outputType = typeArguments[1];
        Class<T> taskType = (Class<T>) typeArguments[2];
        requireStateless(type);
        InputSchema inputSchema = InputSchema.of(typeArguments[0]);
        OutputSchema.of(outputType, type, name);
        Provider<O> output = (Provider<O>) project.getLayout().getBuildDirectory().map(new AssignedOutput(outputType, type, name));

        TaskProvider<T> task = project.getTasks().register(name, taskType, backingTask -> {
            DefaultInputs<I> inputs = new DefaultInputs<>(name, inputSchema);
            configuration.execute(inputs);
            inputs.requireBound();
            TaskBackedWork<I, O, T> work = project.getObjects().newInstance(type);
            work.configure(backingTask, new DefaultBoundInputs<>(inputSchema, inputs.bindings(), project.getProviders(), project.getObjects()), output);
        });
        // map, not flatMap: the result must depend on the task, and the location is not a property of the task.
        return task.map(new TaskOutput<>(output));
    }

    @Override
    public void lifecycle(String name, Provider<?>... outputs) {
        TaskContainer tasks = project.getTasks();
        Action<Task> requireOutputs = task -> task.dependsOn((Object[]) outputs);
        if (tasks.getNames().contains(name)) {
            tasks.named(name, requireOutputs);
        } else {
            tasks.register(name, task -> {
                task.setGroup(GROUP);
                requireOutputs.execute(task);
            });
        }
    }

    // Named classes, not lambdas, so the configuration cache can store the output providers.

    private static final class AssignedOutput implements Transformer<Object, Directory> {
        private final Class<?> outputType;
        private final Class<?> workType;
        private final String name;

        AssignedOutput(Class<?> outputType, Class<?> workType, String name) {
            this.outputType = outputType;
            this.workType = workType;
            this.name = name;
        }

        @Override
        public Object transform(Directory buildDirectory) {
            OutputSchema schema = OutputSchema.of(outputType, workType, name);
            Directory workspace = buildDirectory.dir("work/" + name);
            List<Object> locations = new ArrayList<>();
            for (OutputSchema.Output output : schema.outputs()) {
                if (schema.isSingle() && output.directory()) {
                    locations.add(workspace);
                } else {
                    locations.add(output.directory() ? workspace.dir(output.fileName()) : workspace.file(output.fileName()));
                }
            }
            try {
                return schema.instantiate(locations);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot create the output of work '" + name + "'.", e);
            }
        }
    }

    private static final class TaskOutput<O> implements Transformer<O, Task> {
        private final Provider<O> output;

        TaskOutput(Provider<O> output) {
            this.output = output;
        }

        @Override
        public O transform(Task task) {
            return output.get();
        }
    }

    private static void requireStateless(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    throw new InvalidUserDataException("Work function " + type.getName() + " must be stateless, but has the field '" + field.getName() + "'.");
                }
            }
        }
    }
}
