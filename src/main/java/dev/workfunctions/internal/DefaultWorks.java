package dev.workfunctions.internal;

import dev.workfunctions.Inputs;
import dev.workfunctions.WorkFunction;
import dev.workfunctions.Works;
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

public class DefaultWorks implements Works {

    private static final String GROUP = "work";

    private final Project project;

    @Inject
    public DefaultWorks(Project project) {
        this.project = project;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I, O> Provider<O> work(String name, Class<? extends WorkFunction<I, O>> type, Action<? super Inputs<I>> configuration) {
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
