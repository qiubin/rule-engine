package com.ruleengine.service;

import com.ruleengine.domain.AdapterConfig;
import com.ruleengine.repository.AdapterConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdapterConfigService {

    private final AdapterConfigRepository adapterConfigRepository;

    public List<AdapterConfig> findAll() {
        return adapterConfigRepository.findAll();
    }

    public AdapterConfig findFirst() {
        List<AdapterConfig> list = adapterConfigRepository.findAll();
        if (list.isEmpty()) {
            return createDefault();
        }
        return list.get(0);
    }

    @Transactional
    public AdapterConfig save(AdapterConfig config) {
        // 只保留一条记录
        List<AdapterConfig> existing = adapterConfigRepository.findAll();
        for (AdapterConfig old : existing) {
            if (!old.getId().equals(config.getId())) {
                adapterConfigRepository.delete(old);
            }
        }
        return adapterConfigRepository.save(config);
    }

    @Transactional
    public AdapterConfig createDefault() {
        AdapterConfig config = new AdapterConfig();
        config.setEnabled(false);
        config.setBaseUrl("");
        config.setAdapterPath("/api/v1/adapter/emr");
        config.setAuthType("none");
        config.setConnectTimeoutMs(5000);
        config.setReadTimeoutMs(10000);
        return adapterConfigRepository.save(config);
    }
}
