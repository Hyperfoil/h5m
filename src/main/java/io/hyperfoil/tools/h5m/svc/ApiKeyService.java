package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.ApiKey;
import io.hyperfoil.tools.h5m.entity.Role;
import io.hyperfoil.tools.h5m.api.svc.ApiKeyServiceInterface;
import io.hyperfoil.tools.h5m.entity.ApiKeyEntity;
import io.hyperfoil.tools.h5m.entity.User;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ApiKeyService implements ApiKeyServiceInterface {

    private static final String BOOTSTRAP_USERNAME = "h5m.admin";

    @ConfigProperty(name = "h5m.api-key.expiration-days", defaultValue = "365")
    long expirationDays;

    @ConfigProperty(name = "h5m.bootstrap.api-key")
    Optional<String> bootstrapApiKey;

    @Inject
    UserService userService;

    @Inject
    ApiMapper apiMapper;

    /** Result of creating an API key — holds the DTO (with rawKey populated) for the REST response. */
    public record CreateResult(ApiKey apiKey, String rawKey) {}

    @Override
    @Transactional
    public String create(String username, String description) {
        return createKey(username, description, null).rawKey();
    }

    /**
     * Creates an API key and returns the DTO with rawKey populated.
     * Used by the REST endpoint to return full metadata without re-querying.
     */
    @Transactional
    public CreateResult createAndReturn(String username, String description) {
        return createKey(username, description, null);
    }

    /**
     * Creates an API key with a specific raw key value (instead of generating one).
     * Used for bootstrap where the key is provided via environment variable.
     */
    @Transactional
    public void createWithKey(String username, String description, String rawKey) {
        createKey(username, description, rawKey);
    }

    private CreateResult createKey(String username, String description, String rawKey) {
        User user = userService.byUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        if (rawKey == null) {
            rawKey = "H5M_" + UUID.randomUUID().toString().replace("-", "_").toUpperCase();
        }
        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.keyHash = hashKey(rawKey);
        apiKey.user = user;
        apiKey.description = description;
        apiKey.createdAt = Instant.now();
        apiKey.activeDays = expirationDays;
        apiKey.revoked = false;
        apiKey.persist();
        return new CreateResult(apiMapper.toApiKey(apiKey, rawKey), rawKey);
    }

    @Override
    @Transactional
    public List<ApiKey> listByUser(String username) {
        List<ApiKeyEntity> entities = ApiKeyEntity.find("user.username", username).list();
        return entities.stream().map(apiMapper::toApiKey).toList();
    }

    @Override
    @Transactional
    public ApiKey getById(long keyId) {
        ApiKeyEntity key = ApiKeyEntity.findById(keyId);
        return key != null ? apiMapper.toApiKey(key) : null;
    }

    @Override
    @Transactional
    public void revoke(long keyId) {
        ApiKeyEntity key = ApiKeyEntity.findById(keyId);
        if (key != null) {
            key.revoked = true;
        }
    }

    @Transactional
    public User validateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("H5M_")) {
            return null;
        }
        String hash = hashKey(rawKey);
        ApiKeyEntity apiKey = ApiKeyEntity.find("keyHash", hash).firstResult();
        if (apiKey == null || apiKey.revoked || apiKey.isExpired(Instant.now())) {
            return null;
        }
        apiKey.recordAccess();
        return apiKey.user;
    }

    static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Bootstrap ----

    void onStart(@Observes StartupEvent event) {
        bootstrapApiKey.ifPresent(this::bootstrap);
    }

    @Transactional
    void bootstrap(String rawKey) {
        if (userService.count() > 0) {
            if (userService.byUsername(BOOTSTRAP_USERNAME) != null) {
                Log.warnf("Bootstrap user '%s' still exists — consider removing it in production", BOOTSTRAP_USERNAME);
            }
            return;
        }
        userService.create(BOOTSTRAP_USERNAME, Role.ADMIN);
        createWithKey(BOOTSTRAP_USERNAME, "bootstrap", rawKey);
        Log.infof("Bootstrap: created admin user '%s' with the configured API key", BOOTSTRAP_USERNAME);
    }
}
