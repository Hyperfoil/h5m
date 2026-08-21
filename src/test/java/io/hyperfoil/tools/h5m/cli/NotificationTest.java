package io.hyperfoil.tools.h5m.cli;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import io.quarkus.test.aesh.AeshLauncher;
import io.quarkus.test.aesh.AeshLauncherImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
@TestProfile(CliProfile.class)
public class NotificationTest {

    private AeshLauncher aeshLauncher;

    @BeforeEach
    public void setup(QuarkusMainLauncher launcher) {
        String path = CliProfile.TEST_DB_PATH;
        List.of("", "-shm", "-wal").forEach(suffix -> {
            File f = new File(path + suffix);
            if (f.exists()) {
                f.delete();
            }
        });
        aeshLauncher = new AeshLauncherImpl(launcher);
        aeshLauncher.launch();
    }

    @AfterEach
    public void teardown() {
        if (aeshLauncher != null) {
            aeshLauncher.exit();
        }
    }

    @Test
    public void add_webhook_with_url_option() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "webhook_test"},
                new String[]{"cd", "webhook_test"},
                new String[]{"notification", "add", "WEBHOOK", "--url", "https://hooks.example.com/endpoint"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);
        assertTrue(addOutput.contains("webhook"), "should show method name\n" + addOutput);
        assertTrue(addOutput.contains("'webhook-"), "should show auto-generated name\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("webhook"), "list should show webhook method\n" + listOutput);
        assertTrue(listOutput.contains("webhook-"), "list should show auto-generated name\n" + listOutput);
        assertTrue(listOutput.contains("hooks.example.com"), "list should show webhook URL\n" + listOutput);
    }

    @Test
    public void add_webhook_with_auth_header() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "webhook_auth_test"},
                new String[]{"cd", "webhook_auth_test"},
                new String[]{"notification", "add", "WEBHOOK", "--url", "https://hooks.example.com/ep", "--auth-header", "Bearer mytoken123"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("webhook"), "list should show webhook\n" + listOutput);
    }

    @Test
    public void add_email_with_option() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "email_test"},
                new String[]{"cd", "email_test"},
                new String[]{"notification", "add", "EMAIL", "--email", "team@example.com"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);
        assertTrue(addOutput.contains("email"), "should show method name\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("email"), "list should show email method\n" + listOutput);
        assertTrue(listOutput.contains("team@example.com"), "list should show email address\n" + listOutput);
    }

    @Test
    public void add_email_with_subject() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "email_subj_test"},
                new String[]{"cd", "email_subj_test"},
                new String[]{"notification", "add", "EMAIL", "--email", "alice@example.com,bob@example.com", "--subject", "Perf Alert"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("email"), "list should show email method\n" + listOutput);
        assertTrue(listOutput.contains("alice@example.com"), "list should show recipient\n" + listOutput);
    }

    @Test
    public void add_slack_with_options() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "slack_test"},
                new String[]{"cd", "slack_test"},
                new String[]{"notification", "add", "SLACK", "--channel", "#perf-alerts", "--token", "xoxb-test-token"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);
        assertTrue(addOutput.contains("slack"), "should show method name\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("slack"), "list should show slack method\n" + listOutput);
        assertTrue(listOutput.contains("#perf-alerts"), "list should show channel\n" + listOutput);
    }

    @Test
    public void add_github_issue_with_options() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "github_test"},
                new String[]{"cd", "github_test"},
                new String[]{"notification", "add", "GITHUB_ISSUE", "--owner", "myorg", "--repo", "perf-results", "--token", "ghp_testtoken123"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);
        assertTrue(addOutput.contains("github-issue"), "should show method name\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("github-issue"), "list should show github-issue method\n" + listOutput);
        assertTrue(listOutput.contains("myorg"), "list should show owner\n" + listOutput);
        assertTrue(listOutput.contains("perf-results"), "list should show repo\n" + listOutput);
    }

    @Test
    public void add_github_issue_with_optional_fields() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "github_opts_test"},
                new String[]{"cd", "github_opts_test"},
                new String[]{"notification", "add", "GITHUB_ISSUE", "--owner", "myorg", "--repo", "perf",
                        "--token", "ghp_test", "--title", "Regression detected", "--labels", "regression,automated"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("github-issue"), "list should show github-issue\n" + listOutput);
        assertTrue(listOutput.contains("Regression detected"), "list should show title in data\n" + listOutput);
    }

    @Test
    public void add_with_raw_data_json() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "raw_json_test"},
                new String[]{"cd", "raw_json_test"},
                new String[]{"notification", "add", "WEBHOOK", "--data", "{\"url\":\"https://raw.example.com/hook\"}"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("webhook"), "list should show webhook\n" + listOutput);
        assertTrue(listOutput.contains("raw.example.com"), "list should show URL from raw JSON\n" + listOutput);
    }

    @Test
    public void list_notifications_empty() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "empty_notif_test"},
                new String[]{"cd", "empty_notif_test"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String listOutput = results.get(2);
        assertTrue(listOutput.contains("No notification configs"),
                "should show empty message\n" + listOutput);
    }

    @Test
    public void list_notifications_multiple() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "multi_notif_test"},
                new String[]{"cd", "multi_notif_test"},
                new String[]{"notification", "add", "WEBHOOK", "--url", "https://hook1.example.com"},
                new String[]{"notification", "add", "EMAIL", "--email", "dev@example.com"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String listOutput = results.get(4);
        assertTrue(listOutput.contains("webhook"), "list should show webhook\n" + listOutput);
        assertTrue(listOutput.contains("email"), "list should show email\n" + listOutput);
        assertTrue(listOutput.contains("hook1.example.com"), "list should show webhook URL\n" + listOutput);
        assertTrue(listOutput.contains("dev@example.com"), "list should show email\n" + listOutput);
    }

    @Test
    public void remove_notification_by_id() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "remove_id_test"},
                new String[]{"cd", "remove_id_test"},
                new String[]{"notification", "add", "WEBHOOK", "--url", "https://hook.example.com"},
                new String[]{"notification", "list"},
                // Use id=1 since it's the first entity in a fresh DB
                new String[]{"notification", "remove", "1"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String listBefore = results.get(3);
        assertTrue(listBefore.contains("webhook"), "should have webhook before removal\n" + listBefore);

        String removeOutput = results.get(4);
        assertTrue(removeOutput.contains("Removed"), "should confirm removal\n" + removeOutput);

        String listAfter = results.get(5);
        assertTrue(listAfter.contains("No notification configs"),
                "should show empty after removal\n" + listAfter);
    }

    @Test
    public void remove_notification_by_name() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "remove_name_test"},
                new String[]{"cd", "remove_name_test"},
                new String[]{"notification", "add", "WEBHOOK", "--name", "my-hook", "--url", "https://hook.example.com"},
                new String[]{"notification", "list"},
                new String[]{"notification", "remove", "my-hook"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String listBefore = results.get(3);
        assertTrue(listBefore.contains("my-hook"), "should show name before removal\n" + listBefore);

        String removeOutput = results.get(4);
        assertTrue(removeOutput.contains("Removed"), "should confirm removal\n" + removeOutput);

        String listAfter = results.get(5);
        assertTrue(listAfter.contains("No notification configs"),
                "should show empty after removal\n" + listAfter);
    }

    @Test
    public void add_notification_with_folder_option() {
        // Test using --to instead of cd context
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "folder_opt_test"},
                new String[]{"notification", "add", "WEBHOOK", "--to", "folder_opt_test", "--url", "https://hook.example.com"},
                new String[]{"notification", "list", "--from", "folder_opt_test"},
                // listing all without folder context
                new String[]{"notification", "list"}
        );

        String addOutput = results.get(1);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listByFolder = results.get(2);
        assertTrue(listByFolder.contains("webhook"), "list by folder should show webhook\n" + listByFolder);

        String listAll = results.get(3);
        assertTrue(listAll.contains("webhook"), "list all should show webhook\n" + listAll);
    }

    @Test
    public void add_notification_with_template() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "template_test"},
                new String[]{"cd", "template_test"},
                new String[]{"notification", "add", "WEBHOOK", "--url", "https://hook.example.com",
                        "--template", "Alert: {folderName} - {nodeName} has {changeCount} changes"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "should confirm addition\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("Alert:"), "list should show template\n" + listOutput);
    }

    @Test
    public void add_invalid_method() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "invalid_method_test"},
                new String[]{"cd", "invalid_method_test"},
                new String[]{"notification", "add", "INVALID_METHOD", "--url", "https://hook.example.com"},
                new String[]{"cd", ".."}
        );

        String output = results.get(2);
        // Aesh's EnumConverter provides the error: "Invalid value 'INVALID_METHOD'. Valid values: ..."
        assertTrue(output.contains("Invalid value") || output.contains("invalid_method"),
                "should show invalid method error\n" + output);
    }

    @Test
    public void add_webhook_case_insensitive_method() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "case_test"},
                new String[]{"cd", "case_test"},
                new String[]{"notification", "add", "webhook", "--url", "https://hook.example.com"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("Added"), "lowercase method should work\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("webhook"), "should show webhook\n" + listOutput);
    }

    @Test
    public void add_with_explicit_name() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "named_test"},
                new String[]{"cd", "named_test"},
                new String[]{"notification", "add", "WEBHOOK", "--name", "prod-hook", "--url", "https://hook.example.com"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        assertTrue(addOutput.contains("'prod-hook'"), "should show explicit name\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("prod-hook"), "list should show explicit name\n" + listOutput);
    }

    @Test
    public void add_auto_generated_name() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "autogen_test"},
                new String[]{"cd", "autogen_test"},
                new String[]{"notification", "add", "SLACK", "--channel", "#alerts", "--token", "xoxb-test"},
                new String[]{"notification", "list"},
                new String[]{"cd", ".."}
        );

        String addOutput = results.get(2);
        // Auto-generated name follows pattern: method-id, e.g., "slack-1"
        assertTrue(addOutput.contains("'slack-"), "should show auto-generated name with method prefix\n" + addOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("slack-"), "list should show auto-generated name\n" + listOutput);
    }

    @Test
    public void add_duplicate_name_fails() {
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "dup_test"},
                new String[]{"cd", "dup_test"},
                new String[]{"notification", "add", "WEBHOOK", "--name", "my-hook", "--url", "https://hook1.example.com"},
                new String[]{"notification", "add", "WEBHOOK", "--name", "my-hook", "--url", "https://hook2.example.com"},
                new String[]{"cd", ".."}
        );

        String firstAdd = results.get(2);
        assertTrue(firstAdd.contains("Added"), "first add should succeed\n" + firstAdd);

        String secondAdd = results.get(3);
        assertTrue(secondAdd.contains("already exists"), "duplicate name should fail\n" + secondAdd);
    }

    @Test
    public void remove_with_folder_option() {
        // Test removal using --from instead of cd context
        List<String> results = H5mTest.run(aeshLauncher,
                new String[]{"folder", "add", "remove_from_test"},
                new String[]{"notification", "add", "WEBHOOK", "--to", "remove_from_test", "--name", "hook1", "--url", "https://hook.example.com"},
                new String[]{"notification", "remove", "hook1", "--from", "remove_from_test"},
                new String[]{"notification", "list", "--from", "remove_from_test"}
        );

        String removeOutput = results.get(2);
        assertTrue(removeOutput.contains("Removed"), "should confirm removal\n" + removeOutput);

        String listOutput = results.get(3);
        assertTrue(listOutput.contains("No notification configs"),
                "should show empty after removal\n" + listOutput);
    }
}
