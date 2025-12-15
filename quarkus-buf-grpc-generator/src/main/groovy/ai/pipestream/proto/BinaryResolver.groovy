// Copyright 2023 Buf Technologies, Inc.
// Adapted from https://github.com/bufbuild/buf-gradle-plugin
// Licensed under the Apache License, Version 2.0

package ai.pipestream.proto

import org.gradle.api.Project

/**
 * Resolves and downloads binary tools from Maven Central for protobuf code generation.
 *
 * Downloads platform-specific binaries:
 * - buf CLI: build.buf:buf
 * - protoc: com.google.protobuf:protoc
 * - protoc-gen-grpc-java: io.grpc:protoc-gen-grpc-java
 *
 * All use classifier format: {os}-{arch} (e.g., linux-x86_64, osx-aarch_64)
 */
class BinaryResolver {

    // Configuration names
    static final String BUF_BINARY_CONFIGURATION_NAME = "bufTool"
    static final String PROTOC_CONFIGURATION_NAME = "protocTool"
    static final String GRPC_JAVA_CONFIGURATION_NAME = "grpcJavaTool"

    // Default versions
    static final String DEFAULT_BUF_VERSION = "1.61.0"
    static final String DEFAULT_PROTOC_VERSION = "4.33.2"
    static final String DEFAULT_GRPC_JAVA_VERSION = "1.77.0"

    // ========== Configuration Creation ==========

    /**
     * Creates all binary tool configurations.
     */
    static void createAllConfigurations(Project project) {
        createConfiguration(project, BUF_BINARY_CONFIGURATION_NAME)
        createConfiguration(project, PROTOC_CONFIGURATION_NAME)
        createConfiguration(project, GRPC_JAVA_CONFIGURATION_NAME)
    }

    private static void createConfiguration(Project project, String name) {
        if (!project.configurations.findByName(name)) {
            project.configurations.create(name)
        }
    }

    // ========== Dependency Configuration ==========

    /**
     * Configures the buf binary dependency.
     */
    static void configureBufDependency(Project project, String version) {
        configureDependency(project, BUF_BINARY_CONFIGURATION_NAME,
            "build.buf", "buf", version)
    }

    /**
     * Configures the protoc binary dependency.
     */
    static void configureProtocDependency(Project project, String version) {
        configureDependency(project, PROTOC_CONFIGURATION_NAME,
            "com.google.protobuf", "protoc", version)
    }

    /**
     * Configures the protoc-gen-grpc-java plugin dependency.
     */
    static void configureGrpcJavaDependency(Project project, String version) {
        configureDependency(project, GRPC_JAVA_CONFIGURATION_NAME,
            "io.grpc", "protoc-gen-grpc-java", version)
    }

    private static void configureDependency(Project project, String configName,
                                            String group, String name, String version) {
        def classifier = getPlatformClassifier()

        project.dependencies.add(
            configName,
            [
                group: group,
                name: name,
                version: version,
                classifier: classifier,
                ext: "exe"
            ]
        )

        project.logger.info("Configured ${name} dependency: ${group}:${name}:${version}:${classifier}@exe")
    }

    // ========== Binary Resolution ==========

    /**
     * Resolves the buf executable file.
     */
    static File resolveBufExecutable(Project project) {
        return resolveExecutable(project, BUF_BINARY_CONFIGURATION_NAME)
    }

    /**
     * Resolves the protoc executable file.
     */
    static File resolveProtocExecutable(Project project) {
        return resolveExecutable(project, PROTOC_CONFIGURATION_NAME)
    }

    /**
     * Resolves the protoc-gen-grpc-java executable file.
     */
    static File resolveGrpcJavaExecutable(Project project) {
        return resolveExecutable(project, GRPC_JAVA_CONFIGURATION_NAME)
    }

    private static File resolveExecutable(Project project, String configName) {
        def config = project.configurations.getByName(configName)
        def executable = config.singleFile

        if (!executable.canExecute()) {
            executable.setExecutable(true)
        }

        return executable
    }

    // ========== Platform Detection ==========

    /**
     * Gets the platform classifier for Maven artifacts.
     * Format: {os}-{arch} (e.g., linux-x86_64, osx-aarch_64, windows-x86_64)
     */
    static String getPlatformClassifier() {
        def osName = System.getProperty("os.name").toLowerCase()
        def arch = System.getProperty("os.arch").toLowerCase()
        return "${getOsPart(osName)}-${getArchPart(arch)}"
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
