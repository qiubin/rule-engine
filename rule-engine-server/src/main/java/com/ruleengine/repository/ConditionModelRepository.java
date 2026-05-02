package com.ruleengine.repository;

import com.ruleengine.domain.ConditionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConditionModelRepository extends JpaRepository<ConditionModel, Long> {
    List<ConditionModel> findByCategoryId(Long categoryId);
    long countByCategoryId(Long categoryId);
}
