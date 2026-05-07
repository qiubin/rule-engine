package com.ruleengine.script;

import com.ruleengine.nlp.NlpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * NLP 脚本服务：支持在规则执行时调用医学 NLP 能力。
 * 通过全局变量 nlpUtils 注入到 Drools 会话中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlpScriptService {

    private final NlpService nlpService;

    /**
     * 检查文本中是否包含某类医学实体。
     *
     * @param text       病历文本
     * @param entityType 实体类型：symptoms / signs / drugs / exams / surgeries / diseases
     * @return 包含至少一个该类型实体时返回 true
     */
    public boolean medicalNerContains(String text, String entityType) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        Map<String, List<String>> entities = nlpService.medicalNer(text);
        List<String> list = entities.get(entityType);
        return list != null && !list.isEmpty();
    }

    /**
     * 检查文本中是否包含指定的医学实体（精确匹配）。
     *
     * @param text       病历文本
     * @param entityType 实体类型
     * @param entityName 具体实体名称
     * @return 包含该实体时返回 true
     */
    public boolean medicalNerContains(String text, String entityType, String entityName) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(entityName)) {
            return false;
        }
        Map<String, List<String>> entities = nlpService.medicalNer(text);
        List<String> list = entities.getOrDefault(entityType, Collections.emptyList());
        return list.contains(entityName);
    }

    /**
     * 否定检测：判断文本中某实体是否被否定。
     *
     * @param text   病历文本
     * @param entity 实体名称
     * @return 实体被否定时返回 true（如"无发热"返回 true）
     */
    public boolean negationCheck(String text, String entity) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(entity)) {
            return false;
        }
        return nlpService.isNegated(text, entity);
    }

    /**
     * 基于 HanLP 分词的 Jaccard 相似度比对。
     *
     * @param a         文本 A
     * @param b         文本 B
     * @param threshold 相似度阈值 [0, 1]
     * @return 相似度 >= 阈值时返回 true
     */
    public boolean tokenSimilarity(String a, String b, double threshold) {
        double sim = nlpService.tokenJaccard(a, b);
        log.debug("tokenSimilarity: sim={}", sim);
        return sim >= threshold;
    }

    /**
     * 检查文本中某类实体是否全被否定（无阳性提及）。
     * 应用场景：主诉中提到症状但全被否定（如"无发热、无咳嗽"），需进一步确认。
     *
     * @param text       病历文本
     * @param entityType 实体类型
     * @return 该类实体全部被否定时返回 true
     */
    public boolean allNegated(String text, String entityType) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        Map<String, List<String>> entities = nlpService.medicalNer(text);
        List<String> list = entities.getOrDefault(entityType, Collections.emptyList());
        if (list.isEmpty()) {
            return false;
        }
        for (String entity : list) {
            if (!nlpService.isNegated(text, entity)) {
                return false;
            }
        }
        return true;
    }
}
