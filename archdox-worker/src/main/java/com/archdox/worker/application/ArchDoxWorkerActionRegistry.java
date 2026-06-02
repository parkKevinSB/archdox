package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ArchDoxWorkerActionRegistry {
    private final Map<ArchDoxWorkerActionType, ArchDoxWorkerActionExecutor> executors;

    public ArchDoxWorkerActionRegistry(Collection<? extends ArchDoxWorkerActionExecutor> executors) {
        var byType = new EnumMap<ArchDoxWorkerActionType, ArchDoxWorkerActionExecutor>(ArchDoxWorkerActionType.class);
        if (executors != null) {
            for (var executor : executors) {
                if (executor == null) {
                    continue;
                }
                var previous = byType.putIfAbsent(executor.actionType(), executor);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate ArchDox Worker action executor: " + executor.actionType());
                }
            }
        }
        this.executors = Map.copyOf(byType);
    }

    public Optional<ArchDoxWorkerActionExecutor> resolve(ArchDoxWorkerActionType actionType) {
        return Optional.ofNullable(executors.get(actionType));
    }

    public Set<ArchDoxWorkerActionType> registeredActionTypes() {
        return executors.keySet();
    }
}
