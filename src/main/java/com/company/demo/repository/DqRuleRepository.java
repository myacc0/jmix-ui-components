package com.company.demo.repository;

import com.company.demo.entity.DqRule;
import io.jmix.core.repository.JmixDataRepository;

import java.util.UUID;

public interface DqRuleRepository extends JmixDataRepository<DqRule, UUID> {
}