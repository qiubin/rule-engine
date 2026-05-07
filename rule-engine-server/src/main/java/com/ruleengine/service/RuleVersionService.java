package com.ruleengine.service;

import com.ruleengine.domain.Rule;
import com.ruleengine.domain.RuleVersion;
import com.ruleengine.domain.enums.RuleStatus;
import com.ruleengine.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleVersionService {

    private final RuleVersionRepository versionRepository;

    @Transactional
    public RuleVersion createVersion(Long ruleId, Integer version, String canvasData, String droolsDrl, String changeNote) {
        RuleVersion rv = new RuleVersion();
        rv.setRuleId(ruleId);
        rv.setVersion(version);
        rv.setCanvasData(canvasData);
        rv.setDroolsDrl(droolsDrl);
        rv.setChangeNote(changeNote);
        return versionRepository.save(rv);
    }

    public List<RuleVersion> findByRuleId(Long ruleId) {
        return versionRepository.findByRuleIdOrderByVersionDesc(ruleId);
    }

    public RuleVersion findById(Long id) {
        return versionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在: " + id));
    }

    public Integer getNextVersion(Long ruleId) {
        return versionRepository.findTopByRuleIdOrderByVersionDesc(ruleId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);
    }

    @Transactional
    public Rule rollback(Rule rule, Long versionId) {
        RuleVersion target = findById(versionId);
        if (!target.getRuleId().equals(rule.getId())) {
            throw new RuntimeException("版本不属于该规则");
        }

        // 创建回滚版本记录
        Integer nextVersion = getNextVersion(rule.getId());
        createVersion(rule.getId(), nextVersion, target.getCanvasData(), target.getDroolsDrl(),
                "回滚到版本 " + target.getVersion());

        // 更新规则当前内容
        rule.setCanvasData(target.getCanvasData());
        rule.setDroolsDrl(target.getDroolsDrl());
        rule.setStatus(RuleStatus.DRAFT);
        rule.setVersion(String.valueOf(nextVersion));
        log.info("规则 [{}] 已回滚到版本 {}，新版本号 {}", rule.getCode(), target.getVersion(), nextVersion);
        return rule;
    }
}
