package com.company.demo.dto.starrockssync;

/**
 * How one sync run went. {@code skipped} counts source rows the target could not accept —
 * currently only orders whose product is absent from the main store.
 */
public record SyncResult(int read, int written, int skipped) {
    @Override
    public String toString() {
        return read + " read, " + written + " written, " + skipped + " skipped";
    }
}
