package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqCheckRunTrigger implements EnumClass<String> {

    MANUAL("manual"),
    SYSTEM("system");

    private final String id;

    DqCheckRunTrigger(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqCheckRunTrigger fromId(String id) {
        for (DqCheckRunTrigger at : DqCheckRunTrigger.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}