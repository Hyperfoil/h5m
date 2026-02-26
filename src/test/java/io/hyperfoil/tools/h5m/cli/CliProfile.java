package io.hyperfoil.tools.h5m.cli;

import io.quarkus.test.junit.QuarkusTestProfile;

public class CliProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        return "cli";
    }
}
