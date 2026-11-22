package dev.workfunctions.internal;

import dev.workfunctions.Inputs;
import dev.workfunctions.TaskBackedWork;
import dev.workfunctions.Work;
import dev.workfunctions.WorkFunction;
import dev.workfunctions.WorkRegistry;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class DefaultWorkRegistry implements WorkRegistry {

    private static final String GROUP = "work";

    private final Project project;
    private final Map<String, Registration> registrations = new HashMap<>();

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

        Provider<?> output = output(task, outputSchema);
        registrations.put(name, new Registration(type, output));
        return (Provider<O>) output;
    }

    private static Provider<?> output(TaskProvider<WorkFunctionTask> task, OutputSchema schema) {
        if (!schema.isSingle()) {
            return task.map(new WorkFunctionTask.OutputValue());
        }
        if (schema.outputs().get(0).directory()) {
            return task.flatMap(new WorkFunctionTask.SingleDirectory());
        }
        return task.flatMap(new WorkFunctionTask.SingleFile());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O> Provider<O> named(String name, Class<? extends Work<O>> type) {
        if (TaskBackedWork.class.isAssignableFrom(type)) {
            return taskOutput(name, (Class<? extends TaskBackedWork<O, Task>>) type);
        }
        Registration registration = registrations.get(name);
        if (registration == null) {
            throw new InvalidUserDataException("Work '" + name + "' is not registered.");
        }
        if (!type.isAssignableFrom(registration.type())) {
            throw new InvalidUserDataException("Work '" + name + "' is " + registration.type().getName() + ", not " + type.getName() + ".");
        }
        return (Provider<O>) registration.output();
    }

    @SuppressWarnings("unchecked")
    private <O, T extends Task> Provider<O> taskOutput(String name, Class<? extends TaskBackedWork<O, T>> type) {
        Class<T> taskType = (Class<T>) TypeArguments.of(type, TaskBackedWork.class)[1];
        requireStateless(type);
        TaskContainer tasks = project.getTasks();
        if (!tasks.getNames().contains(name)) {
            throw new InvalidUserDataException("Work '" + name + "' is not registered. " + type.getName() + " needs an existing " + taskType.getSimpleName() + " task with this name.");
        }
        TaskBackedWork<O, T> work = project.getObjects().newInstance(type);
        return tasks.named(name, taskType).flatMap(work::output);
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

    private record Registration(Class<?> type, Provider<?> output) {
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
