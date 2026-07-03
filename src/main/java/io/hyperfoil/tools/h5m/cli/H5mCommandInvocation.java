package io.hyperfoil.tools.h5m.cli;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.Executor;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationConfiguration;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.shell.Shell;
import org.aesh.command.validator.CommandValidatorException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.KeyAction;

public class H5mCommandInvocation implements CommandInvocation {

    private final CommandInvocation delegate;
    private final FolderContext folderContext;

    H5mCommandInvocation(CommandInvocation delegate, FolderContext folderContext) {
        this.delegate = delegate;
        this.folderContext = folderContext;
    }

    public String getFolderName() {
        return folderContext.getFolderName();
    }

    public void setFolderName(String folderName) {
        folderContext.setFolderName(folderName);
        if (folderName != null) {
            delegate.setPrompt(new Prompt("[h5m:" + folderName + "]$ "));
        } else {
            delegate.setPrompt(new Prompt("[h5m]$ "));
        }
    }

    public boolean hasFolderContext() {
        return folderContext.isSet();
    }

    public void clearFolderContext() {
        setFolderName(null);
    }

    @Override
    public Shell getShell() {
        return delegate.getShell();
    }

    @Override
    public void setPrompt(Prompt prompt) {
        delegate.setPrompt(prompt);
    }

    @Override
    public Prompt getPrompt() {
        return delegate.getPrompt();
    }

    @Override
    public String getHelpInfo(String commandName) {
        return delegate.getHelpInfo(commandName);
    }

    @Override
    public String getHelpInfo() {
        return delegate.getHelpInfo();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public CommandInvocationConfiguration getConfiguration() {
        return delegate.getConfiguration();
    }

    @Override
    public KeyAction input() throws InterruptedException {
        return delegate.input();
    }

    @Override
    public KeyAction input(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.input(timeout, unit);
    }

    @Override
    public String inputLine() throws InterruptedException {
        return delegate.inputLine();
    }

    @Override
    public String inputLine(Prompt prompt) throws InterruptedException {
        return delegate.inputLine(prompt);
    }

    @Override
    public void print(String msg, boolean paging) {
        delegate.print(msg, paging);
    }

    @Override
    public void println(String msg, boolean paging) {
        delegate.println(msg, paging);
    }

    @Override
    public Executor<? extends CommandInvocation> buildExecutor(String line)
            throws CommandNotFoundException, CommandLineParserException,
            OptionValidatorException, CommandValidatorException, IOException {
        return delegate.buildExecutor(line);
    }

    @Override
    public void executeCommand(String input) throws CommandNotFoundException,
            CommandLineParserException, OptionValidatorException,
            CommandValidatorException, CommandException, InterruptedException, IOException {
        delegate.executeCommand(input);
    }
}
