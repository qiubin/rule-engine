package com.ruleengine.controller;

import com.ruleengine.domain.DataElement;
import com.ruleengine.dto.DataElementImportResult;
import com.ruleengine.service.DataElementImportService;
import com.ruleengine.service.DataElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/data-elements")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DataElementController {

    private final DataElementService service;
    private final DataElementImportService importService;

    @GetMapping
    public ResponseEntity<List<DataElement>> list(@RequestParam(required = false) Long datasetId) {
        if (datasetId != null) {
            return ResponseEntity.ok(service.findByDatasetId(datasetId));
        }
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataElement> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<DataElement> create(@RequestBody DataElement element) {
        return ResponseEntity.ok(service.save(element));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataElement> update(@PathVariable Long id, @RequestBody DataElement element) {
        element.setId(id);
        return ResponseEntity.ok(service.save(element));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/import")
    public ResponseEntity<DataElementImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }
        DataElementImportResult result = importService.importFromExcel(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(out, com.ruleengine.dto.DataElementImportRow.class)
                .sheet("数据元导入模板")
                .doWrite(Collections.emptyList());
        byte[] bytes = out.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data-element-import-template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
