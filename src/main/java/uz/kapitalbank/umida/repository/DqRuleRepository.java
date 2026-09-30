package uz.kapitalbank.umida.repository;

import uz.kapitalbank.umida.entity.dq.DqRule;
import io.jmix.core.repository.JmixDataRepository;

import java.util.UUID;

public interface DqRuleRepository extends JmixDataRepository<DqRule, UUID> {
}