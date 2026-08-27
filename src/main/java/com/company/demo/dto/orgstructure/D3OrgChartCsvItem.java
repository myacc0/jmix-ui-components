package com.company.demo.dto.orgstructure;

import com.opencsv.bean.CsvBindByName;

public class D3OrgChartCsvItem {
    @CsvBindByName(column = "id")
    private String id;

    @CsvBindByName(column = "parentId")
    private String parentId;

    @CsvBindByName(column = "name")
    private String name;

    @CsvBindByName(column = "lastName")
    private String lastName;

    @CsvBindByName(column = "positionName")
    private String position;

    @CsvBindByName(column = "imageUrl")
    private String image;

    @CsvBindByName(column = "email")
    private String email;

    @CsvBindByName(column = "office")
    private String department;

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

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return "D3OrgChartCsvItem{" +
                "id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                ", name='" + name + '\'' +
                ", lastName='" + lastName + '\'' +
                ", position='" + position + '\'' +
                ", image='" + image + '\'' +
                ", email='" + email + '\'' +
                ", department='" + department + '\'' +
                '}';
    }
}
