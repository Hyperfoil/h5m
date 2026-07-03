package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.TeamServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

@CommandDefinition(name = "create-team", description = "Create a new team for organizing access control", generateHelp = true)
public class AdminCreateTeam implements Command<H5mCommandInvocation> {

    @Inject
    TeamServiceInterface teamService;

    @Argument(description = "team name")
    public String name;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        long id = teamService.create(name);
        invocation.println("Created team: " + name + " (id=" + id + ")");
        return CommandResult.SUCCESS;
    }
}
