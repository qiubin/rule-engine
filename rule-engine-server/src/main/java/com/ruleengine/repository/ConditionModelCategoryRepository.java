package com.ruleengine.repository;

import com.ruleengine.domain.ConditionModelCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConditionModelCategoryRepository extends JpaRepository<ConditionModelCategory, Long> {
    Optional<ConditionModelCategory> findByCode(String code);
}
