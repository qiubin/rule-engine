package com.ruleengine.repository;

import com.ruleengine.domain.RuleExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleExecutionLogRepository extends JpaRepository<RuleExecutionLog, Long> {
    List<RuleExecutionLog> findByRuleIdOrderByExecutedAtDesc(Long ruleId);
}
