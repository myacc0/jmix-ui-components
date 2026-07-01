package com.company.demo.dto;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

@JmixEntity(name = "demo_DqDataSource")
public class DqDataSource {
    @JmixId
    private String id;

    @InstanceName
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

    @Override
    public String toString() {
        return "DqDataSource{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}