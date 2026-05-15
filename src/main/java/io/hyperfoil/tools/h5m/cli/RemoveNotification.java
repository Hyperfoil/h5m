package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import jakarta.transaction.Transactional;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "notification", separator = " ",
    description = "remove a notification config by ID",
    mixinStandardHelpOptions = true)
public class RemoveNotification implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "1", description = "notification config ID")
    long configId;

    @Override
    @Transactional
    public Integer call() throws Exception {
        boolean deleted = NotificationConfig.deleteById(configId);
        if (deleted) {
            System.out.println("Removed notification config " + configId);
            return 0;
        } else {
            System.err.println("Notification config not found: " + configId);
            return 1;
        }
    }
}
