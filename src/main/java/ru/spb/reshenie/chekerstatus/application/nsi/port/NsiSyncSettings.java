package ru.spb.reshenie.chekerstatus.application.nsi.port;

import java.util.List;

public interface NsiSyncSettings {

    int getPageSize();

    int getProgressGitLinkWeight();

    boolean isSyncOnStartup();

    long getPollFixedDelayMs();

    long getPollInitialDelayMs();

    List<String> getEnabledDictionaryIdentifiers();
}
