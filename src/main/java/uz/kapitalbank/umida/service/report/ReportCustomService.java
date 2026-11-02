package uz.kapitalbank.umida.service.report;

import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;

import java.util.Collection;
import java.util.List;

/** The code of a report and the rules of its domains. */
@Service
public class ReportCustomService {

    public static final String CODE_PREFIX = "R-";

    /** Created by Liquibase; the start value here only matters if the sequence is missing. */
    private static final Sequence CODE_SEQUENCE = Sequence.withName("UMIDA_REPORT_CODE_SEQ")
            .setStartValue(1000)
            .setIncrement(1);

    private final Sequences sequences;

    public ReportCustomService(Sequences sequences) {
        this.sequences = sequences;
    }

    public String generateCode() {
        return CODE_PREFIX + sequences.createNextValue(CODE_SEQUENCE);
    }

    /** The domains a report may have as secondary ones, given its primary domain: all of them but that one. */
    public List<DictDataDomain> withoutPrimaryDomain(Collection<DictDataDomain> domains,
                                                     @Nullable DictDataDomain primaryDomain) {
        return domains.stream()
                .filter(domain -> !domain.equals(primaryDomain))
                .toList();
    }
}
