package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.ApiKeyServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

@CommandDefinition(name = "revoke-api-key", description = "Revoke an existing API key", generateHelp = true)
public class AdminRevokeApiKey implements Command<H5mCommandInvocation> {

    @Inject
    ApiKeyServiceInterface apiKeyService;

    @Argument(description = "API key ID", required = true)
    public long keyId;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        apiKeyService.revoke(keyId);
        invocation.println("API key " + keyId + " revoked.");
        return CommandResult.SUCCESS;
    }
}
