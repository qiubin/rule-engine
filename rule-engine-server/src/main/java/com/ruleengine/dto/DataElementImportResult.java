package com.ruleengine.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据元批量导入结果
 */
@Data
public class DataElementImportResult {

    private int totalRows;
    private int dataSetCreated;
    private int dataSetUpdated;
    private int dataElementCreated;
    private int dataElementUpdated;
    private int conditionModelCreated;
    private List<ErrorItem> errors = new ArrayList<>();

    @Data
    public static class ErrorItem {
        private int row;
        private String standardCode;
        private String camelName;
        private String message;

        public ErrorItem(int row, String standardCode, String camelName, String message) {
            this.row = row;
            this.standardCode = standardCode;
            this.camelName = camelName;
            this.message = message;
        }
    }
}
