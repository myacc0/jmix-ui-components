package uz.kapitalbank.umida.enums.report;

import io.jmix.core.metamodel.datatype.EnumClass;
import org.springframework.lang.Nullable;

/**
 * The order of the tiles of a folder in the list of reports: by the localized name or by the time of creation.
 * The groups stay before the reports, each of them in this order.
 */
public enum ReportTileSort implements EnumClass<String> {

    NAME_ASC("nameAsc"),
    NAME_DESC("nameDesc"),
    NEWEST_FIRST("newest"),
    OLDEST_FIRST("oldest");

    private final String id;

    ReportTileSort(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static ReportTileSort fromId(String id) {
        for (ReportTileSort value : ReportTileSort.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
