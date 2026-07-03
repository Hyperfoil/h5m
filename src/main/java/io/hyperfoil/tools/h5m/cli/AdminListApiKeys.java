package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.ApiKey;
import io.hyperfoil.tools.h5m.api.svc.ApiKeyServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import java.util.List;

@CommandDefinition(name = "list-api-keys", description = "List API keys for a user", generateHelp = true)
public class AdminListApiKeys implements Command<H5mCommandInvocation> {

    @Inject
    ApiKeyServiceInterface apiKeyService;

    @Argument(description = "username", required = true)
    public String username;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        List<ApiKey> keys = apiKeyService.listByUser(username);
        invocation.println(ListCmd.table(100, keys,
                List.of("id", "description", "created", "last_used", "revoked", "expired"),
                List.of(k -> String.valueOf(k.id()),
                        k -> k.description() != null ? k.description() : "",
                        k -> k.createdAt() != null ? k.createdAt().toString() : "",
                        k -> k.lastUsedAt() != null ? k.lastUsedAt().toString() : "never",
                        k -> String.valueOf(k.revoked()),
                        k -> String.valueOf(k.expired()))));
        return CommandResult.SUCCESS;
    }
}
