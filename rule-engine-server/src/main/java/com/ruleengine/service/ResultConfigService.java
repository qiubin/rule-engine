package com.ruleengine.service;

import com.ruleengine.domain.ResultConfig;
import com.ruleengine.repository.ResultConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultConfigService {

    private final ResultConfigRepository resultConfigRepository;

    public List<ResultConfig> findAll() {
        return resultConfigRepository.findAll();
    }

    public List<ResultConfig> findByConditionModelId(Long conditionModelId) {
        return resultConfigRepository.findByConditionModelId(conditionModelId);
    }

    public ResultConfig findById(Long id) {
        return resultConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("结果配置不存在: " + id));
    }

    @Transactional
    public ResultConfig save(ResultConfig resultConfig) {
        return resultConfigRepository.save(resultConfig);
    }

    @Transactional
    public void deleteById(Long id) {
        resultConfigRepository.deleteById(id);
    }
}
