package com.ruleengine.service;

import com.ruleengine.domain.ConditionModelCategory;
import com.ruleengine.repository.ConditionModelCategoryRepository;
import com.ruleengine.repository.ConditionModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConditionModelCategoryService {

    private final ConditionModelCategoryRepository repository;
    private final ConditionModelRepository conditionModelRepository;

    public List<ConditionModelCategory> findAll() {
        return repository.findAll();
    }

    public ConditionModelCategory findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("条件分类不存在: " + id));
    }

    @Transactional
    public ConditionModelCategory save(ConditionModelCategory category) {
        return repository.save(category);
    }

    @Transactional
    public void deleteById(Long id) {
        long count = conditionModelRepository.countByCategoryId(id);
        if (count > 0) {
            throw new RuntimeException("该分类下存在 " + count + " 个条件，无法删除");
        }
        repository.deleteById(id);
    }
}
