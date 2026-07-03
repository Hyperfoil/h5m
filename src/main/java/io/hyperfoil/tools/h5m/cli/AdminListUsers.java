package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import io.hyperfoil.tools.h5m.entity.User;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import java.util.List;

@CommandDefinition(name = "list-users", description = "List all registered users", generateHelp = true)
public class AdminListUsers implements Command<H5mCommandInvocation> {

    @Inject
    UserServiceInterface userService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        List<User> users = userService.list();
        invocation.println(ListCmd.table(80, users,
                List.of("username", "role"),
                List.of(u -> u.username, u -> u.role.name())));
        return CommandResult.SUCCESS;
    }
}
