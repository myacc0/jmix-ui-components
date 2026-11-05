package uz.kapitalbank.umida.service.report;

/**
 * A rule of the access to the reports that an operation of {@link ReportAccessService} would break, such as
 * a second pending request for the same report. The message is localized and meant for the user.
 */
public class ReportAccessException extends RuntimeException {

    public ReportAccessException(String message) {
        super(message);
    }
}
