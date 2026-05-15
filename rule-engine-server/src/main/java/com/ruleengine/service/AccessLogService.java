package com.ruleengine.service;

import com.ruleengine.domain.AccessLog;
import com.ruleengine.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessLogService {

    private final AccessLogRepository repository;

    @Transactional
    public AccessLog save(AccessLog accessLog) {
        return repository.save(accessLog);
    }

    public Page<AccessLog> search(String pageName, String clientIp, String requestPath,
                                   String startTime, String endTime, Pageable pageable) {
        Specification<AccessLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pageName != null && !pageName.isEmpty()) {
                predicates.add(cb.like(root.get("pageName"), "%" + pageName + "%"));
            }
            if (clientIp != null && !clientIp.isEmpty()) {
                predicates.add(cb.like(root.get("clientIp"), "%" + clientIp + "%"));
            }
            if (requestPath != null && !requestPath.isEmpty()) {
                predicates.add(cb.like(root.get("requestPath"), "%" + requestPath + "%"));
            }
            if (startTime != null && !startTime.isEmpty()) {
                try {
                    LocalDateTime st = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    predicates.add(cb.greaterThanOrEqualTo(root.get("accessTime"), st));
                } catch (Exception ignored) {}
            }
            if (endTime != null && !endTime.isEmpty()) {
                try {
                    LocalDateTime et = LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    predicates.add(cb.lessThanOrEqualTo(root.get("accessTime"), et));
                } catch (Exception ignored) {}
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
