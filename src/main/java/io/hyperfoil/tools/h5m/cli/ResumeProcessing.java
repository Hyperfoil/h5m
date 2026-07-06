package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.svc.ProcessingService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name="resume",description = "resume incomplete processing events")
public class ResumeProcessing implements Callable<Integer> {

    @Inject
    ProcessingService service;

    @Override
    public Integer call() throws Exception {
        service.recoverIncompleteProcessing(null);
        return 0;
    }
}
