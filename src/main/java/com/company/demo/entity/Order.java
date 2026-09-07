package com.company.demo.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JmixEntity
@Table(name = "orders")
@Entity(name = "demo_Order")
public class Order {

    @JmixGeneratedValue
    @Column(name = "id", nullable = false)
    @Id
    private Integer id;

    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(name = "total_sum", nullable = false, precision = 20, scale = 4)
    @NotNull
    private BigDecimal totalSum;

    @Column(name = "sale_sum", precision = 20, scale = 4)
    private BigDecimal saleSum;

    @Column(name = "delivery_sum", precision = 20, scale = 4)
    private BigDecimal deliverySum;

    @Column(name = "quantity", nullable = false)
    @NotNull
    private Integer quantity;

    @Column(name = "payment_method", nullable = false, length = 50)
    @NotNull
    private String paymentMethod;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Email
    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "order_date", nullable = false)
    @NotNull
    private LocalDateTime orderDate;

    @Column(name = "notes", length = 65533)
    private String notes;

    @PostConstruct
    public void postConstruct() {
        orderDate = LocalDateTime.now();
    }

    @InstanceName
    @DependsOnProperties({"id", "customerName"})
    public String getInstanceName() {
        return customerName == null ? String.valueOf(id) : id + " - " + customerName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getTotalSum() {
        return totalSum;
    }

    public void setTotalSum(BigDecimal totalSum) {
        this.totalSum = totalSum;
    }

    public BigDecimal getSaleSum() {
        return saleSum;
    }

    public void setSaleSum(BigDecimal saleSum) {
        this.saleSum = saleSum;
    }

    public BigDecimal getDeliverySum() {
        return deliverySum;
    }

    public void setDeliverySum(BigDecimal deliverySum) {
        this.deliverySum = deliverySum;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
