package com.company.demo.entity.starrocks;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.Store;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps the StarRocks {@code dwh.products} table (mirrored from the postgres-dwh store).
 * <p>
 * Notes on the mapping:
 * <ul>
 *     <li>Lives in the {@code starrocks} data store, not the main one.</li>
 *     <li>No {@code @Version} attribute: the StarRocks table has no VERSION column,
 *         so declaring one would break every query against it.</li>
 *     <li>{@code id} is a {@code VARCHAR(36)} holding UUID text — StarRocks has no
 *         native uuid type — so it is mapped as String and generated on create.</li>
 * </ul>
 */
@JmixEntity
@Store(name = "starrocks")
@Table(name = "products")
@Entity(name = "demo_StarrocksProduct")
public class Product {

    @Column(name = "id", nullable = false, length = 36)
    @Id
    private String id;

    @InstanceName
    @Column(name = "name", nullable = false, length = 255)
    @NotNull
    private String name;

    @Column(name = "description", length = 65533)
    private String description;

    @Column(name = "price", nullable = false, precision = 20, scale = 4)
    @NotNull
    private BigDecimal price;

    @Column(name = "sale", precision = 20, scale = 4)
    private BigDecimal sale;

    @Column(name = "category", nullable = false)
    @NotNull
    private Integer category;

    @Column(name = "quantity", nullable = false)
    @NotNull
    private Integer quantity = 0;

    @Column(name = "created_at", nullable = false)
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PostConstruct
    public void postConstruct() {
        id = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getSale() {
        return sale;
    }

    public void setSale(BigDecimal sale) {
        this.sale = sale;
    }

    public Integer getCategory() {
        return category;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
