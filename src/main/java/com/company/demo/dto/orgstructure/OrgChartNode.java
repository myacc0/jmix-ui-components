package com.company.demo.dto.orgstructure;

/**
 * One node of the d3-org-chart rendered in {@code OrgStructureView}.
 * <p>
 * A node is normally a {@code Position}: the node title is the department name, the
 * main line is the employee's full name and the sub-line is the job title. A department
 * that has no head position gets a synthetic node carrying only the department name,
 * so that the chart stays connected and single-rooted.
 * <p>
 * Plain POJO on purpose — the instances are only serialized to JSON and passed to
 * {@code D3OrgChart.setData(String)}, they are never bound to a Jmix data container.
 */
public class OrgChartNode {

    private String id;
    private String parentId;

    /** Node title — the department name (read by {@code d.data.orgLevelName} in orgchart.js). */
    private String orgLevelName;
    /** Employee full name, empty for a vacant or synthetic node. */
    private String name;
    /** Job title name, empty for a synthetic department node. */
    private String position;
    /** Avatar URL; null renders the initials placeholder. */
    private String image;
    /** Employee e-mail, empty for a vacant or synthetic node. */
    private String email;

    private String departmentId;
    private String positionId;
    private String employeeId;
    private String status;
    private boolean head;
    private boolean departmentNode;

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

    public String getOrgLevelName() {
        return orgLevelName;
    }

    public void setOrgLevelName(String orgLevelName) {
        this.orgLevelName = orgLevelName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getPositionId() {
        return positionId;
    }

    public void setPositionId(String positionId) {
        this.positionId = positionId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isHead() {
        return head;
    }

    public void setHead(boolean head) {
        this.head = head;
    }

    public boolean isDepartmentNode() {
        return departmentNode;
    }

    public void setDepartmentNode(boolean departmentNode) {
        this.departmentNode = departmentNode;
    }

    @Override
    public String toString() {
        return "OrgChartNode{" +
                "id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                ", orgLevelName='" + orgLevelName + '\'' +
                ", name='" + name + '\'' +
                ", position='" + position + '\'' +
                '}';
    }
}
