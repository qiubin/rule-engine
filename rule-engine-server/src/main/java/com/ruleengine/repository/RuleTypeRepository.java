package com.ruleengine.repository;

import com.ruleengine.domain.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleTypeRepository extends JpaRepository<RuleType, Long> {
    Optional<RuleType> findByCode(String code);
    List<RuleType> findAllByOrderBySortOrderAsc();
}
