package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

@JmixEntity
@Table(name = "UMIDA_ORG_STRUCTURE_SUBDIVISIONS", indexes = {
        @Index(name = "IDX_UMIDA_ORG_STRUCTURE_SUBDIVISION_PARENT", columnList = "PARENT_ID")
})
@Entity(name = "umida_OrgStructureSubdivision")
public class OrgStructureSubdivision {

    @Id
    @Column(name = "ID", nullable = false, length = 36)
    private String id;

    @InstanceName
    @Column(name = "NAME", length = 300)
    private String name;

    @JoinColumn(name = "PARENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private OrgStructureSubdivision parent;

    public OrgStructureSubdivision getParent() {
        return parent;
    }

    public void setParent(OrgStructureSubdivision parent) {
        this.parent = parent;
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

    @Override
    public String toString() {
        return "OrgStructureSubdivision{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", parent=" + parent +
                '}';
    }
}
