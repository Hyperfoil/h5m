package io.hyperfoil.tools.h5m.svc;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SecurityEnabledProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "h5m.security.enabled", "true",
                "quarkus.oidc.tenant-enabled", "false", // disables the start of a oidc server. we're testing API keys, no need for OIDC
                "quarkus.oidc-proxy.enabled", "false" // oidc-proxy disabled in tests: no frontend needs the /q/oidc discovery endpoint
        );
    }
}
