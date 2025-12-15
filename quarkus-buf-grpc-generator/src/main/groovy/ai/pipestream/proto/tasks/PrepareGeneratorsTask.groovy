package ai.pipestream.proto.tasks

import ai.pipestream.proto.BufPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Prepares code generator plugins and creates buf.gen.yaml configuration.
 *
 * This task:
 * 1. Resolves the Quarkus gRPC protoc plugin JAR from Maven
 * 2. Creates a shell wrapper script (protoc-gen-mutiny) that invokes the Java generator
 * 3. Generates a buf.gen.yaml with absolute paths for all plugins
 * 4. Includes any extra plugins configured by the user
 */
abstract class PrepareGeneratorsTask extends DefaultTask {

    /**
     * Quarkus gRPC version for resolving the Mutiny generator.
     */
    @Input
    abstract Property<String> getQuarkusGrpcVersion()

    /**
     * Whether to generate Quarkus Mutiny stubs.
     */
    @Input
    abstract Property<Boolean> getGenerateMutiny()

    /**
     * Whether to generate standard gRPC stubs.
     */
    @Input
    abstract Property<Boolean> getGenerateGrpc()

    /**
     * Output directory for generated Java sources (used in buf.gen.yaml).
     * Marked @Internal because DirectoryProperty can't be @Input directly.
     */
    @Internal
    abstract DirectoryProperty getOutputDir()

    /**
     * The output directory path as a string input for up-to-date checking.
     * If the output path changes, the task will re-run.
     */
    @Input
    String getOutputDirPath() {
        return getOutputDir().get().asFile.absolutePath
    }

    /**
     * Directory for protoc plugin scripts.
     */
    @OutputDirectory
    abstract DirectoryProperty getPluginDir()

    /**
     * The generated buf.gen.yaml file.
     */
    @OutputFile
    abstract RegularFileProperty getBufGenYaml()

    /**
     * Extra buf plugins to include in generation.
     * Marked @Internal because the container itself isn't serializable.
     */
    @Internal
    Iterable<BufPlugin> extraPlugins

    /**
     * The protoc executable (resolved from Maven Central).
     * Used for local Java codegen via protoc_builtin.
     * Ignored if customProtocPath is set.
     */
    @InputFiles
    @org.gradle.api.tasks.Optional
    abstract ConfigurableFileCollection getProtocExecutable()

    /**
     * The protoc-gen-grpc-java executable (resolved from Maven Central).
     * Used for local gRPC codegen.
     * Ignored if customGrpcJavaPath is set.
     */
    @InputFiles
    @org.gradle.api.tasks.Optional
    abstract ConfigurableFileCollection getGrpcJavaExecutable()

    /**
     * Optional: Custom path to protoc binary.
     * If set, this path is used instead of downloading from Maven.
     */
    @Input
    @org.gradle.api.tasks.Optional
    abstract Property<String> getCustomProtocPath()

    /**
     * Optional: Custom path to protoc-gen-grpc-java binary.
     * If set, this path is used instead of downloading from Maven.
     */
    @Input
    @org.gradle.api.tasks.Optional
    abstract Property<String> getCustomGrpcJavaPath()

    @TaskAction
    void prepare() {
        def pluginDir = getPluginDir().get().asFile
        def bufGenYaml = getBufGenYaml().get().asFile
        def outputDir = getOutputDir().get().asFile.absolutePath
        def generateMutiny = getGenerateMutiny().get()
        def generateGrpc = getGenerateGrpc().get()

        pluginDir.mkdirs()

        // Resolve protoc executable - use custom path if specified, otherwise download from Maven
        def protocPath = resolveProtocPath()
        logger.lifecycle("Using protoc: ${protocPath}")

        // Resolve grpc-java executable - use custom path if specified, otherwise download from Maven
        def grpcJavaPath = resolveGrpcJavaPath()
        logger.lifecycle("Using protoc-gen-grpc-java: ${grpcJavaPath}")

        String mutinyPluginPath = null
        if (generateMutiny) {
            mutinyPluginPath = this.prepareMutinyGenerator(pluginDir)
        }

        this.generateBufGenYaml(bufGenYaml, outputDir, protocPath, grpcJavaPath, generateGrpc, mutinyPluginPath, extraPlugins)

        logger.lifecycle("Generated buf.gen.yaml at: ${bufGenYaml}")
    }

    /**
     * Resolves the protoc path - uses custom path if specified, otherwise downloads from Maven.
     */
    private String resolveProtocPath() {
        def customPath = getCustomProtocPath().getOrNull()
        if (customPath) {
            def customFile = new File(customPath)
            if (!customFile.exists()) {
                throw new GradleException("Custom protoc path does not exist: ${customPath}")
            }
            if (!customFile.canExecute()) {
                customFile.setExecutable(true)
            }
            logger.lifecycle("Using custom protoc path: ${customPath}")
            return customPath
        }
        return resolveExecutable(getProtocExecutable())
    }

    /**
     * Resolves the grpc-java plugin path - uses custom path if specified, otherwise downloads from Maven.
     */
    private String resolveGrpcJavaPath() {
        def customPath = getCustomGrpcJavaPath().getOrNull()
        if (customPath) {
            def customFile = new File(customPath)
            if (!customFile.exists()) {
                throw new GradleException("Custom grpc-java plugin path does not exist: ${customPath}")
            }
            if (!customFile.canExecute()) {
                customFile.setExecutable(true)
            }
            logger.lifecycle("Using custom grpc-java plugin path: ${customPath}")
            return customPath
        }
        return resolveExecutable(getGrpcJavaExecutable())
    }

    /**
     * Resolves an executable from a file collection and ensures it's executable.
     */
    private String resolveExecutable(ConfigurableFileCollection fileCollection) {
        def executable = fileCollection.singleFile
        if (!executable.canExecute()) {
            executable.setExecutable(true)
        }
        return executable.absolutePath
    }

    /**
     * Resolves the Quarkus gRPC protoc plugin and creates a wrapper script.
     * Returns the absolute path to the wrapper script.
     */
    private String prepareMutinyGenerator(File pluginDir) {
        def version = getQuarkusGrpcVersion().get()
        logger.lifecycle("Resolving quarkus-grpc-protoc-plugin:${version}")

        // Create a detached configuration to resolve the plugin JAR and its dependencies
        def config = project.configurations.detachedConfiguration(
            project.dependencies.create("io.quarkus:quarkus-grpc-protoc-plugin:${version}")
        )

        def files = config.resolve()
        if (files.isEmpty()) {
            throw new GradleException("Failed to resolve quarkus-grpc-protoc-plugin:${version}")
        }

        // Build classpath string
        def classpath = files.collect { it.absolutePath }.join(File.pathSeparator)
        logger.debug("Mutiny generator classpath: ${classpath}")

        // Create wrapper script
        def scriptFile = new File(pluginDir, "protoc-gen-mutiny")
        scriptFile.text = """\
#!/bin/sh
# Generated by pipestream-proto-toolchain
# Wrapper for Quarkus Mutiny gRPC generator
set -e
exec java -cp "${classpath}" io.quarkus.grpc.protoc.plugin.MutinyGrpcGenerator "\$@"
"""
        scriptFile.setExecutable(true)

        // TODO: Add Windows .bat support if needed
        // def batFile = new File(pluginDir, "protoc-gen-mutiny.bat")
        // batFile.text = "@echo off\r\njava -cp \"${classpath.replace(':', ';')}\" ..."

        logger.lifecycle("Created Mutiny generator wrapper: ${scriptFile}")
        return scriptFile.absolutePath
    }

    /**
     * Generates the buf.gen.yaml configuration file with LOCAL plugins (no BSR uploads).
     *
     * Uses buf.gen.yaml v2 format with:
     * - protoc_builtin: java (uses local protoc for Java POJOs)
     * - local: path/to/protoc-gen-grpc-java (uses local gRPC plugin)
     *
     * This ensures NO proto files are ever uploaded to buf.build servers.
     */
    protected void generateBufGenYaml(File yamlFile, String outputDir, String protocPath,
                                      String grpcJavaPath, boolean generateGrpc,
                                      String mutinyPluginPath, Iterable<BufPlugin> extraPlugins) {
        def yaml = new StringBuilder()
        yaml.append("# Generated by pipestream-proto-toolchain\n")
        yaml.append("# DO NOT EDIT - regenerate with ./gradlew prepareGenerators\n")
        yaml.append("# Uses 100% LOCAL generation - no proto files uploaded to BSR\n")
        yaml.append("version: v2\n")
        yaml.append("plugins:\n")

        // Standard Java POJOs using protoc_builtin (local protoc)
        // protoc_path tells buf where to find the protoc binary
        yaml.append("  - protoc_builtin: java\n")
        yaml.append("    out: ${outputDir}\n")
        yaml.append("    protoc_path: ${protocPath}\n")

        // Standard gRPC stubs using local plugin
        // Note: local plugins do NOT use protoc_path - they are protoc plugins invoked by buf
        if (generateGrpc) {
            yaml.append("  - local: ${grpcJavaPath}\n")
            yaml.append("    out: ${outputDir}\n")
        }

        // Quarkus Mutiny stubs using local plugin
        if (mutinyPluginPath) {
            yaml.append("  - local: ${mutinyPluginPath}\n")
            yaml.append("    out: ${outputDir}\n")
            yaml.append("    opt:\n")
            yaml.append("      - quarkus.generate-code.grpc.scan-for-imports=none\n")
        }

        // Extra plugins configured by user
        // Note: These may still use remote plugins if user configures them that way
        if (extraPlugins) {
            extraPlugins.each { plugin ->
                def pluginRef = plugin.plugin.get()

                // Check if it's a local path or a remote reference
                if (pluginRef.startsWith('/') || pluginRef.startsWith('./')) {
                    yaml.append("  - local: ${pluginRef}\n")
                } else {
                    // User explicitly wants remote plugin (their choice)
                    yaml.append("  - remote: ${pluginRef}\n")
                }

                // Resolve output path - make it absolute if relative
                def pluginOut = plugin.out.get()
                if (!pluginOut.startsWith('/')) {
                    pluginOut = project.file(pluginOut).absolutePath
                }
                yaml.append("    out: ${pluginOut}\n")

                // Add options if present
                def opts = plugin.opt.getOrNull()
                if (opts && !opts.isEmpty()) {
                    yaml.append("    opt:\n")
                    opts.each { opt ->
                        yaml.append("      - ${opt}\n")
                    }
                }
            }
        }

        yamlFile.text = yaml.toString()
    }
}
