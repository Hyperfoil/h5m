package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.cli.CliProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(CliProfile.class)
public class FolderServiceSqliteTest extends FolderServiceTest{}
