package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.cli.CliProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import java.util.Map;

@QuarkusTest
@TestProfile(CliProfile.class)
public class ValueServiceSqliteTest extends ValueServiceTest{}
