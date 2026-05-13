package com.ruleengine.service;

import com.ruleengine.domain.RuleType;
import com.ruleengine.repository.RuleRepository;
import com.ruleengine.repository.RuleTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleTypeService {

    private final RuleTypeRepository ruleTypeRepository;
    private final RuleRepository ruleRepository;

    public List<RuleType> findAll() {
        return ruleTypeRepository.findAllByOrderBySortOrderAsc();
    }

    public RuleType findById(Long id) {
        return ruleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则类型不存在: " + id));
    }

    public RuleType findByCode(String code) {
        return ruleTypeRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("规则类型不存在: " + code));
    }

    @Transactional
    public RuleType save(RuleType ruleType) {
        return ruleTypeRepository.save(ruleType);
    }

    @Transactional
    public void deleteById(Long id) {
        long count = ruleRepository.countByRuleTypeId(id);
        if (count > 0) {
            throw new RuntimeException("该类型下存在 " + count + " 条规则，无法删除");
        }
        ruleTypeRepository.deleteById(id);
    }
}
