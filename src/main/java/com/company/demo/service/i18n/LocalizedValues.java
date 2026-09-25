package com.company.demo.service.i18n;

import io.jmix.core.security.CurrentAuthentication;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Picks the value of a multilingual attribute for the current user's language.
 * <p>
 * Multilingual attributes follow the naming convention {@code xxxRu} / {@code xxxUz}.
 * Russian is the default language and the fallback when the Uzbek value is empty.
 */
@Component
public class LocalizedValues {

    public static final String SUFFIX_RU = "Ru";
    public static final String SUFFIX_UZ = "Uz";

    private final CurrentAuthentication currentAuthentication;

    public LocalizedValues(CurrentAuthentication currentAuthentication) {
        this.currentAuthentication = currentAuthentication;
    }

    public boolean isUz() {
        return "uz".equals(currentAuthentication.getLocale().getLanguage());
    }

    /**
     * @return attribute name suffix of the current language: {@code Ru} or {@code Uz}
     */
    public String currentSuffix() {
        return isUz() ? SUFFIX_UZ : SUFFIX_RU;
    }

    public String pick(String ru, String uz) {
        return isUz() && StringUtils.isNotBlank(uz) ? uz : ru;
    }
}
