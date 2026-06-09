package io.github.frankois944.spmForKmp

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import io.github.frankois944.spmForKmp.config.AppleCompileTarget
import io.github.frankois944.spmForKmp.fixture.KotlinSource
import io.github.frankois944.spmForKmp.fixture.SmpKMPTestFixture
import io.github.frankois944.spmForKmp.fixture.SwiftSource
import io.github.frankois944.spmForKmp.utils.BaseTest
import org.junit.jupiter.api.Test

/**
 * Regression test for multi-target builds that depend on binary `xcframework` artifacts.
 *
 * Before the resolve/compile split, the per-target `swift build` invocations shared a single SPM
 * scratch directory whose resolution outputs (`artifacts/`) were not tracked by any Gradle task.
 * With more than one Apple target in the graph, the second target could fail with
 * `XCFramework Info.plist not found` (upstream issues #294, #298, #309, #312), especially with the
 * build cache enabled. This test builds two targets against a remote binary `xcframework`.
 */
class MultiTargetBinaryPackageTest : BaseTest() {
    @Test
    fun `build with a remote binary package and multiple targets`() {
        // Given
        val fixture =
            SmpKMPTestFixture
                .builder()
                .withBuildPath(testProjectDir.root.absolutePath)
                .withTargets(
                    AppleCompileTarget.iosSimulatorArm64,
                    AppleCompileTarget.iosArm64,
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

        val result =
            GradleBuilder
                .runner(fixture.gradleProject.rootDir, "build")
                .build()

        // Then
        assertThat(result).task(":library:build").succeeded()
    }
}
