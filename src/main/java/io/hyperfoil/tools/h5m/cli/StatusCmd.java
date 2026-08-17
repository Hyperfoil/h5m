package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Processing;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;

import java.util.List;

@CommandDefinition(name = "status", description = "Check processing status of one or more uploads by their processing ID", generateHelp = true)
public class StatusCmd implements Command<H5mCommandInvocation> {

    @Inject
    ProcessingServiceInterface processingService;

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
            Processing status = processingService.getIngestionStatus(id);
            if (status == null) {
                invocation.println(id + ": not found");
            } else {
                invocation.println(id + ": " + status.state());
            }
        }
        return CommandResult.SUCCESS;
    }
}
