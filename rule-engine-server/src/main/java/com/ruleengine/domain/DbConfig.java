package com.ruleengine.domain;

import lombok.Data;

@Data
public class DbConfig {

    private String host = "localhost";
    private Integer port = 3306;
    private String databaseName = "ruleengine";
    private String username = "root";
    private String password = "qiubin78";
    private Boolean useSsl = false;
}
