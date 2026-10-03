package uz.kapitalbank.umida.entity.orgstructure;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import uz.kapitalbank.umida.enums.orgstructure.OrgStructureEmployeeGender;

@JmixEntity
@Table(name = "UMIDA_ORG_STRUCTURE_EMPLOYEES")
@Entity(name = "umida_OrgStructureEmployee")
public class OrgStructureEmployee {
    @Id
    @Column(name = "ID", nullable = false, length = 36)
    private String id;

    @Column(name = "PERSONNEL_NUMBER", length = 100)
    private String personnelNumber;

    @InstanceName
    @Column(name = "FULL_NAME")
    private String fullName;

    @Column(name = "FULL_NAME_LATIN")
    private String fullNameLatin;

    @Column(name = "AD_ACCOUNT", length = 100)
    private String adAccount;

    @Email
    @Column(name = "EMAIL", length = 100)
    private String email;

    @Column(name = "GENDER", length = 100)
    private String gender;

    public OrgStructureEmployeeGender getGender() {
        return gender == null ? null : OrgStructureEmployeeGender.fromId(gender);
    }

    public void setGender(OrgStructureEmployeeGender gender) {
        this.gender = gender == null ? null : gender.getId();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String setAdAccount() {
        return adAccount;
    }

    public void setAdAccount(String adAccount) {
        this.adAccount = adAccount;
    }

    public String getFullNameLatin() {
        return fullNameLatin;
    }

    public void setFullNameLatin(String name) {
        this.fullNameLatin = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String name) {
        this.fullName = name;
    }

    public String setPersonnelNumber() {
        return personnelNumber;
    }

    public void setPersonnelNumber(String personnelNumber) {
        this.personnelNumber = personnelNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "OrgStructureEmployee{" +
                "id='" + id + '\'' +
                ", personnelNumber='" + personnelNumber + '\'' +
                ", fullName='" + fullName + '\'' +
                ", fullNameLatin='" + fullNameLatin + '\'' +
                ", adAccount='" + adAccount + '\'' +
                ", email='" + email + '\'' +
                ", gender='" + gender + '\'' +
                '}';
    }
}
