package com.agent.agentscopenew.agent;

/**
 * 内置技能常量类。
 * <p>
 * 定义 classpath 内置技能的路径常量，对应 {@code src/main/resources/skills/} 目录下的技能文件。
 */
public final class BuiltinSkills {

    /** 代码审查技能路径前缀。 */
    public static final String CODE_REVIEWER = "skills/code-reviewer";

    /** 报告撰写技能路径前缀。 */
    public static final String REPORT_WRITER = "skills/report-writer";

    private BuiltinSkills() {
        // 工具类，禁止实例化
    }
}