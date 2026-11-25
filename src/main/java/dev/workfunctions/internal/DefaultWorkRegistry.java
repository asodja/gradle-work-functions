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
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        Registration registration = new Registration(type);

        TaskProvider<WorkFunctionTask> task = project.getTasks().register(name, WorkFunctionTask.class, workTask -> {
            workTask.setGroup(GROUP);
            workTask.setDescription("Runs " + type.getSimpleName() + ".");
            workTask.initialize(type, inputType, outputType);
            DefaultInputs<I> inputs = new DefaultInputs<>(name, inputSchema, project.getProviders());
            configuration.execute(inputs);
            registration.configured = true;
            for (Action<Object> later : registration.laterConfigurations) {
                later.execute(inputs);
            }
            workTask.bind(inputSchema, inputs.bindings());
        });

        registration.output = output(task, outputSchema);
        registrations.put(name, registration);
        return (Provider<O>) registration.output;
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
    public <O> Provider<O> named(String name, Class<? extends Work<?, O>> type) {
        if (TaskBackedWork.class.isAssignableFrom(type)) {
            return taskOutput(name, (Class<? extends TaskBackedWork<Object, O, Task>>) type, null);
        }
        return (Provider<O>) registration(name, type).output;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Provider<O> named(String name, Class<? extends Work<I, O>> type, Action<? super Inputs<I>> configuration) {
        if (TaskBackedWork.class.isAssignableFrom(type)) {
            return taskOutput(name, (Class<? extends TaskBackedWork<I, O, Task>>) type, configuration);
        }
        Registration registration = registration(name, type);
        if (registration.configured) {
            throw new InvalidUserDataException("Work '" + name + "' is already configured. Its inputs can no longer be bound.");
        }
        registration.laterConfigurations.add((Action<Object>) configuration);
        return (Provider<O>) registration.output;
    }

    private Registration registration(String name, Class<?> type) {
        Registration registration = registrations.get(name);
        if (registration == null) {
            throw new InvalidUserDataException("Work '" + name + "' is not registered.");
        }
        if (!type.isAssignableFrom(registration.type)) {
            throw new InvalidUserDataException("Work '" + name + "' is " + registration.type.getName() + ", not " + type.getName() + ".");
        }
        return registration;
    }

    @SuppressWarnings("unchecked")
    private <I, O, T extends Task> Provider<O> taskOutput(String name, Class<? extends TaskBackedWork<I, O, T>> type, @Nullable Action<? super Inputs<I>> configuration) {
        Class<?>[] typeArguments = TypeArguments.of(type, TaskBackedWork.class);
        Class<T> taskType = (Class<T>) typeArguments[2];
        requireStateless(type);
        InputSchema inputSchema = InputSchema.of(typeArguments[0]);
        TaskContainer tasks = project.getTasks();
        if (!tasks.getNames().contains(name)) {
            throw new InvalidUserDataException("Work '" + name + "' is not registered. " + type.getName() + " needs an existing " + taskType.getSimpleName() + " task with this name.");
        }
        TaskBackedWork<I, O, T> work = project.getObjects().newInstance(type);
        TaskProvider<T> task = tasks.named(name, taskType);
        if (configuration != null) {
            task.configure(backingTask -> {
                DefaultInputs<I> inputs = new DefaultInputs<>(name, inputSchema, project.getProviders());
                configuration.execute(inputs);
                work.configure(backingTask, new DefaultBoundInputs<>(inputSchema, inputs.bindings(), project.getProviders(), project.getObjects()));
            });
        }
        return task.flatMap(work::output);
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

    private static final class Registration {
        final Class<?> type;
        final List<Action<Object>> laterConfigurations = new ArrayList<>();
        Provider<?> output;
        boolean configured;

        Registration(Class<?> type) {
            this.type = type;
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
