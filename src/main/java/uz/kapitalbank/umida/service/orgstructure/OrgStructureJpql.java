package uz.kapitalbank.umida.service.orgstructure;

/**
 * JPQL conditions shared by the queries over the org structure history.
 * <p>
 * A subdivision, job title or position is closed by {@code isActive = 0}; {@code null} counts as active,
 * because a row created through {@code DataManager} writes {@code null} over the column default.
 * An assignment ({@code OrgStructureAssignments}) is current from its {@code startDate} up to and including
 * its {@code endDate}; a query using {@link #currentAssignment(String)} binds the {@code today} parameter.
 * <p>
 * {@code DqIssueOwnSubdivisionRole} repeats these conditions literally, an annotation needs a constant.
 */
public final class OrgStructureJpql {

    public static final String TODAY_PARAM = "today";

    private OrgStructureJpql() {
    }

    /** The subdivision, job title or position under {@code alias} is not closed. */
    public static String active(String alias) {
        return "(" + alias + ".isActive is null or " + alias + ".isActive = 1)";
    }

    /** The assignment under {@code alias} is in force on {@code :today}. */
    public static String currentAssignment(String alias) {
        return "(" + alias + ".startDate <= :" + TODAY_PARAM
                + " and (" + alias + ".endDate is null or " + alias + ".endDate >= :" + TODAY_PARAM + "))";
    }
}
