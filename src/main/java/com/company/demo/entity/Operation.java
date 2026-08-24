package com.company.demo.entity;

import com.company.demo.enums.OperationClientType;
import com.company.demo.enums.OperationServiceType;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "DEMO_OPERATION")
@Entity(name = "demo_Operation")
public class Operation {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CURRENCY_CODE", nullable = false, length = 4)
    @NotNull
    private String currencyCode;

    @Column(name = "DATE_", nullable = false)
    @NotNull
    private LocalDateTime date;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal amount;

    @Column(name = "CLIENT_ID", nullable = false)
    @NotNull
    private String client;

    @Column(name = "CLIENT_TYPE", length = 50)
    private String clientType;

    @Column(name = "SERVICE_ID")
    private String service;

    public OperationClientType getClientType() {
        return clientType == null ? null : OperationClientType.fromId(clientType);
    }

    public void setClientType(OperationClientType clientType) {
        this.clientType = clientType == null ? null : clientType.getId();
    }

    public OperationServiceType getService() {
        return service == null ? null : OperationServiceType.fromId(service);
    }

    public void setService(OperationServiceType service) {
        this.service = service == null ? null : service.getId();
    }

    public String getClient() {
        return client;
    }

    public void setClient(String clientId) {
        this.client = clientId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}