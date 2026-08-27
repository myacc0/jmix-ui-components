package com.company.demo.entity;

import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_WORK_DISCIPLINE_EXCEPTIONS", indexes = {
        @Index(name = "IDX_DEMO_WORK_DISCIPLINE_EXCEPTION_SUBDIVISION", columnList = "SUBDIVISION_ID"),
        @Index(name = "IDX_DEMO_WORK_DISCIPLINE_EXCEPTION_EMPLOYEE", columnList = "EMPLOYEE_ID"),
        @Index(name = "IDX_DEMO_WORK_DISCIPLINE_EXCEPTION_JOB_TITLE", columnList = "JOB_TITLE_ID")
})
@Entity(name = "demo_WorkDisciplineException")
public class WorkDisciplineException {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "SUBDIVISION_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Department subdivision;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "EMPLOYEE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;

    @OnDeleteInverse(DeletePolicy.UNLINK)
    @JoinColumn(name = "JOB_TITLE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private JobTitle jobTitle;

    @Column(name = "START_DATE")
    private LocalDate startDate;

    @Column(name = "END_DATE")
    private LocalDate endDate;

    @InstanceName
    @Column(name = "DESCRIPTION", length = 2000, columnDefinition = "text")
    private String description;

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JobTitle getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(JobTitle jobTitle) {
        this.jobTitle = jobTitle;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Department getSubdivision() {
        return subdivision;
    }

    public void setSubdivision(Department subdivision) {
        this.subdivision = subdivision;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}