package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@JmixEntity
@Table(name = "UMIDA_ORG_STRUCTURE_JOBTITLES")
@Entity(name = "umida_OrgStructureJobTitle")
public class OrgStructureJobTitle {
    @Column(name = "ID", nullable = false, length = 36)
    @Id
    private String id;

    @InstanceName
    @Column(name = "NAME", length = 400)
    private String name;

    @Column(name = "IS_ACTIVE", columnDefinition = "integer default 1")
    private Integer isActive;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}