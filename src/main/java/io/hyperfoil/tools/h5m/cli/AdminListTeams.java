package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.TeamServiceInterface;
import io.hyperfoil.tools.h5m.entity.Team;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import java.util.List;

@CommandDefinition(name = "list-teams", description = "List all teams", generateHelp = true)
public class AdminListTeams implements Command<H5mCommandInvocation> {

    @Inject
    TeamServiceInterface teamService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        List<Team> teams = teamService.list();
        invocation.println(ListCmd.table(80, teams,
                List.of("name", "members"),
                List.of(t -> t.name, t -> t.members.size())));
        return CommandResult.SUCCESS;
    }
}
