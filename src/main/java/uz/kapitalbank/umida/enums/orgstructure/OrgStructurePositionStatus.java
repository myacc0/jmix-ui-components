package uz.kapitalbank.umida.enums.orgstructure;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum OrgStructurePositionStatus implements EnumClass<String> {

    FILLED("filled"),
    VACANT("vacant"),
    OVERSTAFFED("overstaffed");

    private final String id;

    OrgStructurePositionStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrgStructurePositionStatus fromId(String id) {
        for (OrgStructurePositionStatus at : OrgStructurePositionStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}