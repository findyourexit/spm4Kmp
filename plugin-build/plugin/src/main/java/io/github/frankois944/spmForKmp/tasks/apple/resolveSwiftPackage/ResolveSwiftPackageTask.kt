package io.github.frankois944.spmForKmp.tasks.apple.resolveSwiftPackage

import io.github.frankois944.spmForKmp.operations.printExecLogs
import io.github.frankois944.spmForKmp.tasks.utils.TaskTracer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Resolves the Swift package dependency graph (downloading checkouts and extracting binary
 * `xcframework` artifacts) into the shared SwiftPM scratch directory, ONCE per package.
 *
 * This task owns the *resolution* outputs (`artifacts/`, `checkouts/`, `repositories/`) that
 * SwiftPM shares across every Apple target/triple. The per-target [compile][
 * io.github.frankois944.spmForKmp.tasks.apple.compileSwiftPackage.CompileSwiftPackageTask] tasks
 * depend on this task and consume those directories as inputs, while only owning their own
 * `<triple>/<config>` build directory as output.
 *
 * Splitting resolution from compilation makes Gradle's view of the shared scratch directory sound:
 * the resolution artifacts are produced by exactly one task before any target builds, instead of
 * being an untracked side effect of whichever target happened to build first. This fixes the
 * `XCFramework Info.plist not found` failures reported in multi-target and multi-module builds with
 * the build cache enabled (upstream issues #294, #298, #309, #312), without duplicating downloads
 * per target (which would slow the build down).
 */
internal abstract class ResolveSwiftPackageTask : DefaultTask() {
    @get:Internal
    abstract val workingDir: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageSwift: RegularFileProperty

    @get:Internal
    abstract val packageScratchDir: Property<String>

    @get:Input
    @get:Optional
    abstract val sharedCacheDir: Property<String>

    @get:Input
    @get:Optional
    abstract val sharedConfigDir: Property<String>

    @get:Input
    @get:Optional
    abstract val sharedSecurityDir: Property<String>

    @get:Input
    @get:Optional
    abstract val swiftBinPath: Property<String>

    @get:Input
    @get:Optional
    abstract val toolchain: Property<String>

    @get:OutputDirectory
    abstract val artifactsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val checkoutsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val repositoriesDir: DirectoryProperty

    @get:Input
    abstract val traceEnabled: Property<Boolean>

    @get:Internal
    abstract val storedTraceFile: RegularFileProperty

    @get:Inject
    abstract val execOps: ExecOperations

    init {
        description = "Resolve the Swift package dependencies (shared across targets)"
        group = "io.github.frankois944.spmForKmp.tasks"
        onlyIf {
            HostManager.hostIsMac
        }
    }

    @TaskAction
    fun resolvePackage() {
        val tracer =
            TaskTracer(
                "ResolveSwiftPackageTask",
                traceEnabled.get(),
                outputFile = storedTraceFile.get().asFile,
            )
        tracer.trace("ResolveSwiftPackageTask") {
            val args = buildResolveArgs()

            val standardOutput = ByteArrayOutputStream()
            val errorOutput = ByteArrayOutputStream()
            tracer.trace("resolve") {
                execOps
                    .exec {
                        it.executable = swiftBinPath.orNull ?: "xcrun"
                        it.workingDir = File(workingDir.get())
                        it.args = args
                        it.standardOutput = standardOutput
                        it.errorOutput = errorOutput
                        it.isIgnoreExitValue = true
                        toolchain.orNull?.let { toolchain ->
                            it.environment("TOOLCHAINS", toolchain)
                        }
                    }.also {
                        logger.printExecLogs(
                            "resolvePackage",
                            args,
                            it.exitValue != 0,
                            standardOutput,
                            errorOutput,
                        )
                    }
            }

            // SwiftPM only creates these directories when the corresponding kind of dependency
            // exists. Ensure the declared output directories always exist so Gradle can track them
            // (and so downstream tasks can safely consume them) even for bridge-only packages.
            artifactsDir.get().asFile.mkdirs()
            checkoutsDir.get().asFile.mkdirs()
            repositoriesDir.get().asFile.mkdirs()
        }
        tracer.writeHtmlReport()
    }

    private fun buildResolveArgs(): List<String> =
        buildList {
            if (swiftBinPath.orNull == null) {
                toolchain.orNull?.let {
                    add("--toolchain")
                    add(it)
                }
                add("--sdk")
                add("macosx")
                add("swift")
            }
            add("package")
            add("resolve")
            add("--scratch-path")
            add(packageScratchDir.get())
            sharedCacheDir.orNull?.let {
                add("--cache-path")
                add(it)
            }
            sharedConfigDir.orNull?.let {
                add("--config-path")
                add(it)
            }
            sharedSecurityDir.orNull?.let {
                add("--security-path")
                add(it)
            }
        }
}
