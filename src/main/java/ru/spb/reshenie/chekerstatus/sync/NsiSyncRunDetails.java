package ru.spb.reshenie.chekerstatus.sync;

import java.util.Collections;
import java.util.List;

public class NsiSyncRunDetails {

    private final NsiSyncRun run;
    private final List<NsiSyncRunError> errors;

    public NsiSyncRunDetails(NsiSyncRun run, List<NsiSyncRunError> errors) {
        this.run = run;
        this.errors = Collections.unmodifiableList(errors);
    }

    public NsiSyncRun getRun() {
        return run;
    }

    public List<NsiSyncRunError> getErrors() {
        return errors;
    }
}
