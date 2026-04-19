package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.UserServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "create-user", description = "create a new user", mixinStandardHelpOptions = true)
public class AdminCreateUser implements Callable<Integer> {

    @Inject
    UserServiceInterface userService;

    @CommandLine.Parameters(index = "0", description = "username")
    public String username;

    @CommandLine.Option(names = {"--role"}, description = "role (admin or user)", defaultValue = "user")
    public String role;

    @Override
    public Integer call() {
        if (!"admin".equals(role) && !"user".equals(role)) {
            System.err.println("Invalid role: " + role + ". Must be 'admin' or 'user'");
            return 1;
        }
        long id = userService.create(username, role);
        System.out.println("Created user: " + username + " (id=" + id + ", role=" + role + ")");
        return 0;
    }
}
