package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.ProcessingState;
import io.hyperfoil.tools.h5m.api.svc.WorkServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;

import java.util.List;

@CommandDefinition(name = "status", description = "Check processing status of one or more uploads by their processing ID", generateHelp = true)
public class StatusCmd implements Command<H5mCommandInvocation> {

    @Inject
    WorkServiceInterface workService;

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
            ProcessingState state = workService.getProcessingStatus(id);
            switch (state) {
                case PROCESSING -> invocation.println(id + ": PROCESSING");
                case COMPLETED -> invocation.println(id + ": COMPLETED");
                case FAILED -> invocation.println(id + ": FAILED");
                case NOT_FOUND -> invocation.println(id + ": not found");
            }
        }
        return CommandResult.SUCCESS;
    }
}
