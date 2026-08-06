package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.queue.UploadTracker;
import io.hyperfoil.tools.h5m.svc.WorkService;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;

import java.util.List;
import java.util.Optional;

@CommandDefinition(name = "status", description = "Check processing status of one or more uploads by their processing ID", generateHelp = true)
public class StatusCmd implements Command<H5mCommandInvocation> {

    @Inject
    WorkService workService;

    @Arguments(description = "processing ID(s) to check")
    List<String> ids;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (ids == null || ids.isEmpty()) {
            invocation.println("at least one processing ID is required");
            return CommandResult.FAILURE;
        }
        for (String idStr : ids) {
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                invocation.println(idStr + ": invalid ID");
                continue;
            }
            Optional<UploadTracker> tracker = workService.getTracker(id);
            if (tracker.isPresent()) {
                if (tracker.get().getFuture().isDone()) {
                    if (tracker.get().getFuture().isCompletedExceptionally()) {
                        invocation.println(id + ": FAILED");
                    } else {
                        invocation.println(id + ": COMPLETED");
                    }
                } else {
                    invocation.println(id + ": PROCESSING");
                }
            } else {
                // Tracker already cleaned up — check if root value exists in DB
                ValueEntity rootValue = ValueEntity.findById(id);
                if (rootValue != null && rootValue.node != null && rootValue.node.type() == NodeType.ROOT) {
                    invocation.println(id + ": COMPLETED");
                } else {
                    invocation.println(id + ": not found");
                }
            }
        }
        return CommandResult.SUCCESS;
    }
}
