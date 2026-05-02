package com.ruleengine.service;

import com.ruleengine.domain.ConditionModel;
import com.ruleengine.domain.DataElement;
import com.ruleengine.repository.ConditionModelRepository;
import com.ruleengine.repository.DataElementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConditionModelService {

    private final ConditionModelRepository conditionModelRepository;
    private final DataElementRepository dataElementRepository;

    public List<ConditionModel> findAll() {
        return conditionModelRepository.findAll();
    }

    public ConditionModel findById(Long id) {
        return conditionModelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("条件不存在: " + id));
    }

    public List<ConditionModel> findByCategoryId(Long categoryId) {
        return conditionModelRepository.findByCategoryId(categoryId);
    }

    @Transactional
    public ConditionModel save(ConditionModel conditionModel) {
        if (conditionModel.getDataElementId() != null) {
            DataElement de = dataElementRepository.findById(conditionModel.getDataElementId())
                    .orElseThrow(() -> new RuntimeException("数据元不存在: " + conditionModel.getDataElementId()));
            conditionModel.setCode(de.getCode());
            conditionModel.setName(de.getName());
            conditionModel.setDataType(de.getDataType());
        }
        return conditionModelRepository.save(conditionModel);
    }

    @Transactional
    public void deleteById(Long id) {
        conditionModelRepository.deleteById(id);
    }
}
