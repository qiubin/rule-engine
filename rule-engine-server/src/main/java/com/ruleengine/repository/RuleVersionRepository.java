package com.ruleengine.repository;

import com.ruleengine.domain.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {
    List<RuleVersion> findByRuleIdOrderByVersionDesc(Long ruleId);
    Optional<RuleVersion> findTopByRuleIdOrderByVersionDesc(Long ruleId);
}
