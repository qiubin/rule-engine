package com.ruleengine.repository;

import com.ruleengine.domain.Rule;
import com.ruleengine.domain.enums.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
    Optional<Rule> findByCode(String code);
    List<Rule> findByRuleTypeId(Long ruleTypeId);
    long countByRuleTypeId(Long ruleTypeId);
    List<Rule> findByStatus(RuleStatus status);
}
