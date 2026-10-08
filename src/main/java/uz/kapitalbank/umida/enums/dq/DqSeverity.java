package uz.kapitalbank.umida.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqSeverity implements EnumClass<String> {

    LOW("low", 15),
    MEDIUM("medium", 10),
    HIGH("high", 5),
    CRITICAL("critical", 3);

    private final String id;
    private final int dayCost;

    DqSeverity(String id, int dayCost) {
        this.id = id;
        this.dayCost = dayCost;
    }

    public String getId() {
        return id;
    }

    public int getDayCost() {
        return dayCost;
    }

    @Nullable
    public static DqSeverity fromId(String id) {
        for (DqSeverity at : DqSeverity.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}