package com.umik.tiktoksparkflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;


// 项目入口
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TiktokSparkFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiktokSparkFlowApplication.class, args);
    }

}
