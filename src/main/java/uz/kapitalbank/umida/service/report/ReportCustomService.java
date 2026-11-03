package uz.kapitalbank.umida.service.report;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.EntityUuidGenerator;
import io.jmix.core.Id;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import io.jmix.reports.ReportRepository;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.reports.util.ReportsUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;

import java.util.Collection;
import java.util.List;

/** The code of a report, the rules of its domains and what the list of reports does with a report. */
@Service
public class ReportCustomService {

    public static final String CODE_PREFIX = "R-";

    /** Created by Liquibase; the start value here only matters if the sequence is missing. */
    private static final Sequence CODE_SEQUENCE = Sequence.withName("UMIDA_REPORT_CODE_SEQ")
            .setStartValue(1000)
            .setIncrement(1);

    private final Sequences sequences;
    private final DataManager dataManager;
    private final Metadata metadata;
    private final MetadataTools metadataTools;
    private final AccessManager accessManager;
    private final EntityUuidGenerator entityUuidGenerator;
    private final ReportsUtils reportsUtils;
    private final ReportRepository reportRepository;

    public ReportCustomService(Sequences sequences,
                               DataManager dataManager,
                               Metadata metadata,
                               MetadataTools metadataTools,
                               AccessManager accessManager,
                               EntityUuidGenerator entityUuidGenerator,
                               ReportsUtils reportsUtils,
                               ReportRepository reportRepository) {
        this.sequences = sequences;
        this.dataManager = dataManager;
        this.metadata = metadata;
        this.metadataTools = metadataTools;
        this.accessManager = accessManager;
        this.entityUuidGenerator = entityUuidGenerator;
        this.reportsUtils = reportsUtils;
        this.reportRepository = reportRepository;
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

    /** The check of the add-on's list for its create, copy and import actions. */
    public boolean isCreatePermitted() {
        return crudContext().isCreatePermitted();
    }

    /** The check a standard remove action makes; the tree of the list has no such action. */
    public boolean isDeletePermitted() {
        return crudContext().isDeletePermitted();
    }

    private CrudEntityContext crudContext() {
        CrudEntityContext context = new CrudEntityContext(metadata.getClass(Report.class));
        accessManager.applyRegisteredConstraints(context);
        return context;
    }

    /**
     * Saves a copy of the report, as the add-on's list does it. The copy has no code, so
     * {@link ReportCustomEventListener} generates one of its own.
     */
    public Report copy(Report report) {
        Report source = dataManager.load(Id.of(report))
                .fetchPlan("report.edit")
                .one();
        Report copy = metadataTools.deepCopy(source);
        copy.setId(entityUuidGenerator.generate());
        copy.setName(reportsUtils.generateReportName(source.getName()));
        copy.setCode(null);
        for (ReportTemplate template : copy.getTemplates()) {
            template.setId(entityUuidGenerator.generate());
        }
        return reportRepository.save(copy);
    }
}
