package ru.spb.reshenie.chekerstatus.application.nsi.port;

import ru.spb.reshenie.chekerstatus.domain.nsi.NsiRecordSaveResult;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportDocument;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.domain.nsi.RecordPayload;

import java.util.List;

public interface NsiPersistencePort {

    PassportSaveResult savePassport(PassportDocument passport);

    NsiRecordSaveResult saveLoadedRecords(long dictionaryId,
                                          long versionId,
                                          List<RecordPayload> loadedRecords);
}
