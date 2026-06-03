package ru.spb.reshenie.chekerstatus.application.nsi.port;

import ru.spb.reshenie.chekerstatus.domain.nsi.DataPage;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportDocument;

public interface NsiClientPort {

    PassportDocument fetchPassport(String identifier);

    DataPage fetchDataPage(PassportDocument passport, int page, int size);
}
