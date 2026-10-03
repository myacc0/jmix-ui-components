package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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