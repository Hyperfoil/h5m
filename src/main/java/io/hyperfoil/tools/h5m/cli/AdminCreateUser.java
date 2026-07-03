package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.Role;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "create-user", description = "Create a new user account", generateHelp = true)
public class AdminCreateUser implements Command<H5mCommandInvocation> {

    @Inject
    UserServiceInterface userService;

    @Argument(description = "username")
    public String username;

    @Option(name = "role", acceptNameWithoutDashes = true, description = "role (ADMIN or USER)", defaultValue = {"USER"})
    public Role role;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        long id = userService.create(username, role);
        invocation.println("Created user: " + username + " (id=" + id + ", role=" + role + ")");
        return CommandResult.SUCCESS;
    }
}
