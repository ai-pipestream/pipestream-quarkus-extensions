// Copyright 2023 Buf Technologies, Inc.
// Adapted from https://github.com/bufbuild/buf-gradle-plugin
// Licensed under the Apache License, Version 2.0

package ai.pipestream.proto

import org.gradle.api.Project

/**
 * Resolves and downloads the buf CLI binary from Maven Central.
 *
 * Buf publishes platform-specific binaries to Maven Central with the following coordinates:
 * - Group: build.buf
 * - Name: buf
 * - Classifier: {os}-{arch} (e.g., linux-x86_64, osx-aarch_64, windows-x86_64)
 * - Extension: exe
 */
class BufBinaryResolver {

    static final String BUF_BINARY_CONFIGURATION_NAME = "bufTool"
    static final String DEFAULT_BUF_VERSION = "1.61.0"

    /**
     * Creates the Gradle configuration for the buf binary dependency.
     */
    static void createBufBinaryConfiguration(Project project) {
        if (!project.configurations.findByName(BUF_BINARY_CONFIGURATION_NAME)) {
            project.configurations.create(BUF_BINARY_CONFIGURATION_NAME)
        }
    }

    /**
     * Configures the buf binary dependency with the appropriate OS/arch classifier.
     */
    static void configureBufDependency(Project project, String bufVersion) {
        def osName = System.getProperty("os.name").toLowerCase()
        def osPart = getOsPart(osName)
        def archPart = getArchPart(System.getProperty("os.arch").toLowerCase())

        project.dependencies.add(
            BUF_BINARY_CONFIGURATION_NAME,
            [
                group: "build.buf",
                name: "buf",
                version: bufVersion,
                classifier: "${osPart}-${archPart}",
                ext: "exe"
            ]
        )

        project.logger.info("Configured buf dependency: build.buf:buf:${bufVersion}:${osPart}-${archPart}@exe")
    }

    /**
     * Resolves the buf executable file from the configuration.
     * Downloads the binary if not already cached.
     */
    static File resolveBufExecutable(Project project) {
        def config = project.configurations.getByName(BUF_BINARY_CONFIGURATION_NAME)
        def executable = config.singleFile

        if (!executable.canExecute()) {
            executable.setExecutable(true)
        }

        return executable
    }

    private static String getOsPart(String osName) {
        if (osName.startsWith("windows")) {
            return "windows"
        } else if (osName.startsWith("linux")) {
            return "linux"
        } else if (osName.startsWith("mac") || osName.contains("darwin")) {
            return "osx"
        } else {
            throw new IllegalStateException("Unsupported OS: ${osName}")
        }
    }

    private static String getArchPart(String arch) {
        if (arch in ["x86_64", "amd64"]) {
            return "x86_64"
        } else if (arch in ["arm64", "aarch64"]) {
            return "aarch_64"
        } else {
            throw new IllegalStateException("Unsupported architecture: ${arch}")
        }
    }
}
