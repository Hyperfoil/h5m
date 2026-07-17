package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.entity.Role;
import io.hyperfoil.tools.h5m.entity.User;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * One-time bootstrap of the admin account on startup.
 *
 * <p>If the {@code h5m.bootstrap.api-key} configuration property (or
 * {@code H5M_BOOTSTRAP_API_KEY} environment variable) is set and no users
 * exist in the system, creates an admin user with an API key matching the
 * configured value.</p>
 *
 * <p>This enables automated environments (Testcontainers, CI/CD) to start
 * h5m with security enabled and authenticate using a known API key without
 * requiring OIDC or CLI-based user creation.</p>
 */
@ApplicationScoped
public class BootstrapService {

    private static final String BOOTSTRAP_USERNAME = "admin";

    @Inject
    UserService userService;

    @Inject
    ApiKeyService apiKeyService;

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "h5m.bootstrap.api-key")
    Optional<String> bootstrapApiKey;

    void onStart(@Observes StartupEvent event) {
        bootstrapApiKey.ifPresent(this::bootstrap);
    }

    @Transactional
    void bootstrap(String rawKey) {
        if (User.count() > 0) {
            // Check if bootstrap user still exists — warn if it does
            User bootstrapUser = userService.byUsername(BOOTSTRAP_USERNAME);
            if (bootstrapUser != null) {
                Log.warnf("Bootstrap user '%s' still exists — consider removing it in production", BOOTSTRAP_USERNAME);
            }
            return;
        }
        userService.create(BOOTSTRAP_USERNAME, Role.ADMIN);
        apiKeyService.createWithKey(BOOTSTRAP_USERNAME, "bootstrap", rawKey);
        Log.infof("Bootstrap: created admin user '%s' with the configured API key", BOOTSTRAP_USERNAME);
    }
}
