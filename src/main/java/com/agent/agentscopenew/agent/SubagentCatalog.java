package com.agent.agentscopenew.agent;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置子 Agent 声明目录。
 * <p>
 * 提供编程式声明的演示子 Agent：reviewer（代码审查）、researcher（调研）、note-taker（持久会话笔记）。
 * 这些声明与工作区 {@code subagents/*.md} 文件共存，同名时工作区文件覆盖编程式声明。
 */
public final class SubagentCatalog {

    private SubagentCatalog() {
        // 工具类，禁止实例化
    }

    /**
     * 获取默认的演示子 Agent 声明列表。
     */
    public static List<SubagentDeclaration> defaultDeclarations() {
        List<SubagentDeclaration> list = new ArrayList<>();

        // reviewer：代码审查专家，tools 白名单规避 Plan 限制不传播缺口
        list.add(SubagentDeclaration.builder()
                .name("reviewer")
                .description("代码审查专家。当用户需要 review PR、找代码问题、检查代码规范时使用。")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .temperature(0.2)
                .steps(8)
                .tools(List.of("read_file", "grep_files", "glob_files", "list_files", "execute"))
                .build());

        // researcher：调研员，支持 expose_to_user 演示
        list.add(SubagentDeclaration.builder()
                .name("researcher")
                .description("调研专家。擅长搜索信息、分析问题、整理调研报告。")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .temperature(0.5)
                .steps(15)
                .build());

        // note-taker：笔记员，持久会话演示
        list.add(SubagentDeclaration.builder()
                .name("note-taker")
                .description("笔记员。跨对话轮次积累笔记和要点。")
                .workspaceMode(WorkspaceMode.SHARED)
                .persistSession(true)
                .temperature(0.3)
                .steps(5)
                .build());

        return list;
    }
}