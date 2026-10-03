package uz.kapitalbank.umida.dto.orgstructure;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One node of the d3-org-chart rendered in {@code DataDomainStructureView} and
 * {@code DataProductStructureView} — a data domain or a data product.
 * <p>
 * The chart node shows the business owner (a {@code OrgStructureSubdivision}) on top, the domain /
 * product name in the middle and the head of the business owner (the employee of its
 * {@code ishead = 1} position) at the bottom, with the photo of that head on the left.
 * The remaining attributes are only used by {@code DataAssetCardView} and are left out
 * of the chart JSON.
 * <p>
 * Plain POJO on purpose — the instances are only serialized to JSON and passed to
 * {@code D3OrgChart.setData(String)}, they are never bound to a Jmix data container.
 */
public class DataAssetChartNode {

    private String id;
    private String parentId;

    /** Domain / product short name — the main line of the node, also the source of the initials. */
    private String name;
    /** Business owner subdivision name — the top line of the node. */
    private String businessOwnerName;
    /** Full name of the head of the business owner, empty when the subdivision has no head. */
    private String headName;
    /** Photo URL of the head; null renders the initials of {@link #name}. */
    private String image;

    @JsonIgnore
    private String longName;
    @JsonIgnore
    private String description;
    @JsonIgnore
    private OffsetDateTime createdDate;
    @JsonIgnore
    private LocalDate assignDate;
    @JsonIgnore
    private String headJobTitle;
    @JsonIgnore
    private String headEmail;
    @JsonIgnore
    private List<String> stewardNames = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBusinessOwnerName() {
        return businessOwnerName;
    }

    public void setBusinessOwnerName(String businessOwnerName) {
        this.businessOwnerName = businessOwnerName;
    }

    public String getHeadName() {
        return headName;
    }

    public void setHeadName(String headName) {
        this.headName = headName;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDate getAssignDate() {
        return assignDate;
    }

    public void setAssignDate(LocalDate assignDate) {
        this.assignDate = assignDate;
    }

    public String getHeadJobTitle() {
        return headJobTitle;
    }

    public void setHeadJobTitle(String headJobTitle) {
        this.headJobTitle = headJobTitle;
    }

    public String getHeadEmail() {
        return headEmail;
    }

    public void setHeadEmail(String headEmail) {
        this.headEmail = headEmail;
    }

    public List<String> getStewardNames() {
        return stewardNames;
    }

    public void setStewardNames(List<String> stewardNames) {
        this.stewardNames = stewardNames != null ? stewardNames : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "DataAssetChartNode{" +
                "id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                ", name='" + name + '\'' +
                ", businessOwnerName='" + businessOwnerName + '\'' +
                ", headName='" + headName + '\'' +
                '}';
    }
}
