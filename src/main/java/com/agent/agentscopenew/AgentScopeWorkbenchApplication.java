package com.agent.agentscopenew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.agent.agentscopenew.config.DeepSeekModelProperties;
import com.agent.agentscopenew.config.WorkbenchProperties;

/**
 * AgentScope Workbench 启动类。
 * <p>
 * 企业级多租户 AI Agent 工作台，基于 AgentScope Java 2.0.0 HarnessAgent 构建。
 */
@SpringBootApplication
@EnableConfigurationProperties({WorkbenchProperties.class, DeepSeekModelProperties.class})
public class AgentScopeWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeWorkbenchApplication.class, args);
    }

}