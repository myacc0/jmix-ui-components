package uz.kapitalbank.umida.entity.report;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.entity.annotation.ReplaceEntity;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.reports.entity.Report;
import jakarta.persistence.*;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataProduct;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;

import java.util.List;

/**
 * The report of the Reports add-on with the classification of its data: the sensitivity markers, the
 * data domains and the data products, and its business owner. It replaces {@link Report} everywhere, the add-on included, and is
 * stored in the same {@code REPORT_REPORT} table, which tells the two apart by the {@code DTYPE} column.
 *
 * <p>The code is generated on save when the report has none, see
 * {@link uz.kapitalbank.umida.service.report.ReportCustomEventListener}.
 *
 * <p>The markers are not mandatory, as {@code system} of the add-on: a mandatory attribute makes its check
 * box a required field, and an unchecked box counts as empty, so the report could not be saved.
 *
 * <p>The add-on loads and saves reports with its own fetch plans, so the attributes added here also go
 * into {@code report.view} and {@code report.edit} of {@code fetch-plans.xml}. A new reference to an entity
 * without a UUID id also goes into {@link uz.kapitalbank.umida.service.report.ReportCustomGsonSerializationSupport}.
 */
@JmixEntity
@Entity(name = ReportCustom.ENTITY_NAME)
@DiscriminatorValue(ReportCustom.ENTITY_NAME)
@ReplaceEntity(Report.class)
public class ReportCustom extends Report {

    public static final String ENTITY_NAME = "umida_ReportCustom";

    @Column(name = "PERSONAL_DATA")
    private Boolean personalData = false;

    @Column(name = "CONFIDENTIAL_DATA")
    private Boolean confidentialData = false;

    @Column(name = "COMMERCIAL_DATA")
    private Boolean commercialData = false;

    @Column(name = "BANK_DATA")
    private Boolean bankData = false;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "DOMAIN_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private DictDataDomain domain;

    @JoinTable(name = "UMIDA_REPORT_SECONDARY_DOMAIN_LINK",
            joinColumns = @JoinColumn(name = "REPORT_ID", referencedColumnName = "ID"),
            inverseJoinColumns = @JoinColumn(name = "DOMAIN_ID", referencedColumnName = "ID"))
    @ManyToMany(fetch = FetchType.LAZY)
    private List<DictDataDomain> secondaryDomains;

    @JoinTable(name = "UMIDA_REPORT_DATA_PRODUCT_LINK",
            joinColumns = @JoinColumn(name = "REPORT_ID", referencedColumnName = "ID"),
            inverseJoinColumns = @JoinColumn(name = "DATA_PRODUCT_ID", referencedColumnName = "ID"))
    @ManyToMany(fetch = FetchType.LAZY)
    private List<DictDataProduct> dataProducts;

    /**
     * The subdivision that ordered the report. Its head, and the delegates the author names among its
     * employees, manage the access to the report, see
     * {@link uz.kapitalbank.umida.service.report.ReportAccessService}. None until the author sets it, and
     * once HR removes the subdivision.
     */
    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "BUSINESS_OWNER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureSubdivision businessOwner;

    public Boolean getPersonalData() {
        return personalData;
    }

    public void setPersonalData(Boolean personalData) {
        this.personalData = personalData;
    }

    public Boolean getConfidentialData() {
        return confidentialData;
    }

    public void setConfidentialData(Boolean confidentialData) {
        this.confidentialData = confidentialData;
    }

    public Boolean getCommercialData() {
        return commercialData;
    }

    public void setCommercialData(Boolean commercialData) {
        this.commercialData = commercialData;
    }

    public Boolean getBankData() {
        return bankData;
    }

    public void setBankData(Boolean bankData) {
        this.bankData = bankData;
    }

    public DictDataDomain getDomain() {
        return domain;
    }

    public void setDomain(DictDataDomain domain) {
        this.domain = domain;
    }

    public List<DictDataDomain> getSecondaryDomains() {
        return secondaryDomains;
    }

    public void setSecondaryDomains(List<DictDataDomain> secondaryDomains) {
        this.secondaryDomains = secondaryDomains;
    }

    public List<DictDataProduct> getDataProducts() {
        return dataProducts;
    }

    public void setDataProducts(List<DictDataProduct> dataProducts) {
        this.dataProducts = dataProducts;
    }

    public OrgStructureSubdivision getBusinessOwner() {
        return businessOwner;
    }

    public void setBusinessOwner(OrgStructureSubdivision businessOwner) {
        this.businessOwner = businessOwner;
    }
}
