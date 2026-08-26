package com.company.demo.entity.orgstructure;

import com.company.demo.enums.orgstructure.PositionStatus;
import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_POSITION", indexes = {
        @Index(name = "IDX_DEMO_POSITION_DEPARTMENT", columnList = "DEPARTMENT_ID"),
        @Index(name = "IDX_DEMO_POSITION_JOB_TITLE", columnList = "JOB_TITLE_ID"),
        @Index(name = "IDX_DEMO_POSITION_EMPLOYEE", columnList = "EMPLOYEE_ID")
})
@Entity(name = "demo_Position")
public class Position {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "DEPARTMENT_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "JOB_TITLE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private JobTitle jobTitle;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "EMPLOYEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;

    @NumberFormat(pattern = "#")
    @Column(name = "LVL")
    private Integer lvl;

    @NumberFormat(pattern = "#")
    @Column(name = "ISHEAD")
    private Integer ishead;

    @Column(name = "STATUS", length = 50)
    private String status;

    public PositionStatus getStatus() {
        return status == null ? null : PositionStatus.fromId(status);
    }

    public void setStatus(PositionStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public Integer getIshead() {
        return ishead;
    }

    public void setIshead(Integer ishead) {
        this.ishead = ishead;
    }

    public Integer getLvl() {
        return lvl;
    }

    public void setLvl(Integer lvl) {
        this.lvl = lvl;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(JobTitle jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}