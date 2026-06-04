package ru.spb.reshenie.chekerstatus.config.nsi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "nsi")
public class NsiProperties {

    private String userKey;
    private String passportUrlTemplate;
    private String dataUrlTemplate;
    private int pageSize = 100;
    private int progressGitLinkWeight = 100;
    private long pollFixedDelayMs = 3_600_000L;
    private long pollInitialDelayMs = 60_000L;
    private boolean trustAllSsl = true;
    private boolean syncOnStartup = true;
    private List<Dictionary> dictionaries = new ArrayList<Dictionary>();

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getPassportUrlTemplate() {
        return passportUrlTemplate;
    }

    public void setPassportUrlTemplate(String passportUrlTemplate) {
        this.passportUrlTemplate = passportUrlTemplate;
    }

    public String getDataUrlTemplate() {
        return dataUrlTemplate;
    }

    public void setDataUrlTemplate(String dataUrlTemplate) {
        this.dataUrlTemplate = dataUrlTemplate;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getProgressGitLinkWeight() {
        return progressGitLinkWeight;
    }

    public void setProgressGitLinkWeight(int progressGitLinkWeight) {
        this.progressGitLinkWeight = progressGitLinkWeight;
    }

    public long getPollFixedDelayMs() {
        return pollFixedDelayMs;
    }

    public void setPollFixedDelayMs(long pollFixedDelayMs) {
        this.pollFixedDelayMs = pollFixedDelayMs;
    }

    public long getPollInitialDelayMs() {
        return pollInitialDelayMs;
    }

    public void setPollInitialDelayMs(long pollInitialDelayMs) {
        this.pollInitialDelayMs = pollInitialDelayMs;
    }

    public boolean isTrustAllSsl() {
        return trustAllSsl;
    }

    public void setTrustAllSsl(boolean trustAllSsl) {
        this.trustAllSsl = trustAllSsl;
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }

    public List<Dictionary> getDictionaries() {
        return dictionaries;
    }

    public void setDictionaries(List<Dictionary> dictionaries) {
        this.dictionaries = dictionaries;
    }

    public List<String> getEnabledDictionaryIdentifiers() {
        List<String> identifiers = new ArrayList<String>();
        if (dictionaries == null) {
            return identifiers;
        }
        for (Dictionary dictionary : dictionaries) {
            if (dictionary.isEnabled()
                    && dictionary.getIdentifier() != null
                    && !dictionary.getIdentifier().trim().isEmpty()) {
                identifiers.add(dictionary.getIdentifier().trim());
            }
        }
        return identifiers;
    }

    public static class Dictionary {
        private String identifier;
        private boolean enabled = true;

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
