package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.ApiKeyServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "create-api-key", description = "Generate a new API key for programmatic access", generateHelp = true)
public class AdminCreateApiKey implements Command<H5mCommandInvocation> {

    @Inject
    ApiKeyServiceInterface apiKeyService;

    @Argument(description = "username", required = true)
    public String username;

    @Option(name = "description", acceptNameWithoutDashes = true, description = "key description", defaultValue = "")
    public String description;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        String rawKey = apiKeyService.create(username, description);
        invocation.println("API key created for user: " + username);
        invocation.println("Key: " + rawKey);
        invocation.println("WARNING: This key cannot be retrieved again. Store it securely.");
        return CommandResult.SUCCESS;
    }
}
