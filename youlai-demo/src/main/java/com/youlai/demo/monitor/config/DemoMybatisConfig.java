package com.youlai.demo.monitor.config;

import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.youlai.demo.monitor.interceptor.SqlMonitorInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo 模块 MyBatis 扩展配置。
 */
@Configuration
public class DemoMybatisConfig {

    @Bean
    public SqlSessionFactoryBeanCustomizer demoSqlMonitorCustomizer(
            SqlMonitorInterceptor sqlMonitorInterceptor,
            ObjectProvider<MybatisPlusInterceptor> mybatisPlusInterceptorProvider
    ) {
        return factoryBean -> {
            List<Interceptor> interceptors = new ArrayList<>();
            mybatisPlusInterceptorProvider.ifAvailable(interceptors::add);
            interceptors.add(sqlMonitorInterceptor);
            factoryBean.setPlugins(interceptors.toArray(new Interceptor[0]));
        };
    }
}
