package io.github.frankois944.spmForKmp

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import io.github.frankois944.spmForKmp.config.AppleCompileTarget
import io.github.frankois944.spmForKmp.fixture.KotlinSource
import io.github.frankois944.spmForKmp.fixture.SmpKMPTestFixture
import io.github.frankois944.spmForKmp.fixture.SwiftSource
import io.github.frankois944.spmForKmp.utils.BaseTest
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Soundness tests for the shared SwiftPM scratch directory once resolution is split into a
 * dedicated [ResolveSwiftPackageTask]. These exercise the exact failure modes reported upstream:
 *
 * - Incremental rebuilds must be UP-TO-DATE (no perpetual re-run) — the performance concern that
 *   blocked the per-target-scratch approach (PR #302).
 * - A binary build must survive a warm Gradle build cache after a clean — the soundness bug from
 *   #298/#309/#312 where a cached compile would skip `swift build`, leaving `artifacts/` empty and
 *   producing `XCFramework Info.plist not found`.
 */
class ScratchCachingSoundnessTest : BaseTest() {
    private fun binaryFixture(gradleCache: Boolean) =
        SmpKMPTestFixture
            .builder()
            .withBuildPath(testProjectDir.root.absolutePath)
            .withGradleCache(gradleCache)
            .withTargets(
                AppleCompileTarget.iosArm64,
                AppleCompileTarget.iosSimulatorArm64,
            ).withRawDependencies(
                KotlinSource.of(
                    content =
                        """
                        remoteBinary(
                            url = URI("https://spmforkmp.eu/DummyFrameworkV2.xcframework.zip"),
                            checksum = "90da1dfbf1b52b647958974002a329a60e291b463fcb69a53e2e42b74ead0a94",
                            packageName = "DummyFramework",
                            exportToKotlin = true
                        )
                        """.trimIndent(),
                ),
            ).withKotlinSources(
                KotlinSource.of(
                    imports = listOf("DummyFramework.DummyFrameworkVersionNumber"),
                ),
            ).withSwiftSources(
                SwiftSource.of(
                    content =
                        """
                        import Foundation
                        import DummyFramework
                        @objc public class MySwiftClass: NSObject {
                        }
                        """.trimIndent(),
                ),
            ).build()

    @Test
    fun `an unchanged rebuild keeps the resolve and compile tasks up-to-date`() {
        // Given
        val fixture = binaryFixture(gradleCache = false)
        val root = fixture.gradleProject.rootDir
        val resolveTask = ":library:SwiftPackageConfigAppleDummyResolveSwiftPackage"
        val compileTask = ":library:SwiftPackageConfigAppleDummyCompileSwiftPackageIosArm64"

        // When — build twice with no changes in between
        val first = GradleBuilder.runner(root, "build").build()
        assertThat(first).task(":library:build").succeeded()

        val second = GradleBuilder.runner(root, "build").build()

        // Then — nothing changed, so the resolution and compilation must not run again.
        // (A perpetual re-run here would mean `swift build` mutates the resolve task's declared
        // outputs, or the tasks declare their inputs/outputs incorrectly.)
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(resolveTask)?.outcome,
            "ResolveSwiftPackageTask should be UP-TO-DATE on an unchanged rebuild",
        )
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(compileTask)?.outcome,
            "CompileSwiftPackageTask should be UP-TO-DATE on an unchanged rebuild",
        )
    }

    @Test
    fun `a binary build survives a warm build cache across a clean`() {
        // Given
        val fixture = binaryFixture(gradleCache = true)
        val root = fixture.gradleProject.rootDir
        val resolveTask = ":library:SwiftPackageConfigAppleDummyResolveSwiftPackage"
        val compileTask = ":library:SwiftPackageConfigAppleDummyCompileSwiftPackageIosArm64"

        // 1) Populate the build cache.
        val first = GradleBuilder.runner(root, "build", "--build-cache").build()
        assertThat(first).task(":library:build").succeeded()

        // 2) Wipe build outputs (including the SPM scratch directory) while keeping the build cache.
        GradleBuilder.runner(root, "clean", "--build-cache").build()

        // 3) Rebuild from a warm cache. Before the resolve/compile split this could fail with
        //    "XCFramework Info.plist not found" because a cached compile would skip `swift build`
        //    and never repopulate the shared `artifacts/` directory.
        val second = GradleBuilder.runner(root, "build", "--build-cache").build()
        assertThat(second).task(":library:build").succeeded()

        // The (non-cacheable) resolve task must have actually run again to repopulate artifacts/
        // after the clean, rather than being skipped.
        val resolveOutcome = second.task(resolveTask)?.outcome
        assertNotNull(resolveOutcome, "ResolveSwiftPackageTask should be part of the rebuild graph")
        assertNotEquals(
            TaskOutcome.SKIPPED,
            resolveOutcome,
            "ResolveSwiftPackageTask must re-run after a clean to repopulate the shared artifacts/",
        )

        // The compile task is restored from the warm cache (it does NOT re-run `swift build`).
        // This is precisely the condition that used to leave artifacts/ empty and fail with
        // "XCFramework Info.plist not found"; it now succeeds because the resolve task repopulated
        // the shared artifacts/ directory first.
        assertEquals(
            TaskOutcome.FROM_CACHE,
            second.task(compileTask)?.outcome,
            "CompileSwiftPackageTask should be restored FROM_CACHE on the warm-cache rebuild",
        )
    }
}
