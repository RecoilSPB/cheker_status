package ru.spb.reshenie.chekerstatus.application.nsi.port;

import java.util.List;

public interface NsiSyncSettings {

    int getPageSize();

    boolean isSyncOnStartup();

    List<String> getEnabledDictionaryIdentifiers();
}
