package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.TeamServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.Team;
import io.hyperfoil.tools.h5m.entity.User;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "add-member", description = "Add a user to a team", generateHelp = true)
public class AdminAddMember implements Command<H5mCommandInvocation> {

    @Inject
    TeamServiceInterface teamService;

    @Inject
    UserServiceInterface userService;

    @Argument(description = "username")
    public String username;

    @Option(name = "team", acceptNameWithoutDashes = true, description = "team name")
    public String teamName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        User user = userService.byUsername(username);
        if (user == null) {
            invocation.println("User not found: " + username);
            return CommandResult.FAILURE;
        }
        Team team = teamService.byName(teamName);
        if (team == null) {
            invocation.println("Team not found: " + teamName);
            return CommandResult.FAILURE;
        }
        teamService.addMember(team.id, user.id);
        invocation.println("Added " + username + " to team " + teamName);
        return CommandResult.SUCCESS;
    }
}
