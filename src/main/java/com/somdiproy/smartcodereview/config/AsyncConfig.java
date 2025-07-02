package com.somdiproy.smartcodereview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    @Bean(name = "lambdaTaskExecutor")
    public ThreadPoolTaskExecutor lambdaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Lambda-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600); // 10 minutes
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(1800000); // 30 minutes in milliseconds
        // Use the correct method name based on Spring version
        try {
            configurer.setTaskExecutor(lambdaTaskExecutor());
        } catch (Exception e) {
            // For older Spring versions, try alternative method
            configurer.registerCallableInterceptors();
        }
    }
    
    @Bean
    public Executor taskExecutor() {
        return lambdaTaskExecutor();
    }
}