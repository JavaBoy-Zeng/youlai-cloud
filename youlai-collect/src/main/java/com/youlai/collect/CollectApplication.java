package com.youlai.collect;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.youlai.collect.mapper")
public class CollectApplication {

    /**
     * 启动数据采集服务。
     */
    public static void main(String[] args) {
        SpringApplication.run(CollectApplication.class, args);
    }
}
