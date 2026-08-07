package com.agent.agentscopenew.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 沙箱镜像基线校验器（FR-3.4）。
 * <p>
 * SANDBOX 模式 Agent 启动时校验：Docker 守护进程可达、镜像含 POSIX
 * 工具链（sh/ls/cat）。校验失败抛出 {@link IllegalStateException} 给出明确报错，
 * 可通过 {@code sandbox.check-on-start: false} 跳过。
 */
@Slf4j
public final class SandboxImageValidator {

    /** 镜像必须包含的 POSIX 工具（文件工具链依赖）。 */
    private static final List<String> REQUIRED_TOOLS = List.of("sh", "ls", "cat");

    /** 进程等待超时（分钟），覆盖镜像首次拉取耗时。 */
    private static final long PROCESS_TIMEOUT_MINUTES = 5;

    private SandboxImageValidator() {
    }

    /**
     * 校验 Docker 可达性与镜像工具链基线。
     *
     * @param image 沙箱镜像名（如 ubuntu:24.04）
     * @throws IllegalStateException Docker 不可达、命令不可用或镜像缺工具时抛出
     */
    public static void validate(String image) {
        // 1) Docker 守护进程探活
        int infoCode = runProcess("docker", "info");
        if (infoCode != 0) {
            throw new IllegalStateException("Docker 沙箱校验失败：docker info 返回码 " + infoCode
                    + "，请确认 Docker 守护进程已启动（SANDBOX 模式必需）");
        }
        // 2) 镜像基线检查：sh/ls/cat 命令必须存在
        int baseCode = runProcess("docker", "run", "--rm", image,
                "sh", "-c", "command -v sh && command -v ls && command -v cat");
        if (baseCode != 0) {
            throw new IllegalStateException("Docker 沙箱镜像 [" + image + "] 基线校验失败："
                    + "缺少 POSIX 工具链（必需：" + REQUIRED_TOOLS + "），"
                    + "请更换基础镜像或安装工具（check-on-start: false 可跳过校验）");
        }
        log.info("Docker 沙箱镜像 [{}] 基线校验通过", image);
    }

    /**
     * 执行进程并等待完成，返回退出码。
     *
     * @param command 命令及参数
     * @return 退出码；超时返回 -1
     * @throws IllegalStateException Docker 命令不可执行时抛出
     */
    private static int runProcess(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (IOException e) {
            throw new IllegalStateException("无法执行 Docker 命令：" + e.getMessage()
                    + "（SANDBOX 模式需要安装并启动 Docker）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker 命令执行被中断", e);
        }
    }
}
