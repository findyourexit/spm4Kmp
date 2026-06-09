package io.github.frankois944.spmForKmp

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import io.github.frankois944.spmForKmp.config.AppleCompileTarget
import io.github.frankois944.spmForKmp.fixture.KotlinSource
import io.github.frankois944.spmForKmp.fixture.SmpKMPTestFixture
import io.github.frankois944.spmForKmp.fixture.SwiftSource
import io.github.frankois944.spmForKmp.utils.BaseTest
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Faithful regression test for upstream issue #294: a `localPackage` whose source product
 * transitively depends on a *remote* binary `xcframework` (`.binaryTarget(url:checksum:)`).
 *
 * The transitive binary must be downloaded and extracted into the shared scratch `artifacts/`
 * before any target builds; with multiple targets this previously failed on the second target with
 * `XCFramework Info.plist not found`.
 */
class LocalPackageTransitiveBinaryTest : BaseTest() {
    @Test
    fun `build a local package whose product transitively depends on a remote binary for multiple targets`() {
        val localPackageDirectory = File("src/functionalTest/resources/LocalBinaryDependentPackage")
        // Given
        val fixture =
            SmpKMPTestFixture
                .builder()
                .withBuildPath(testProjectDir.root.absolutePath)
                .withTargets(
                    AppleCompileTarget.iosArm64,
                    AppleCompileTarget.iosSimulatorArm64,
                ).withRawDependencies(
                    KotlinSource.of(
                        content =
                            """
                            localPackage(
                                path = "${localPackageDirectory.absolutePath}",
                                packageName = "LocalBinaryDependentPackage",
                                products = {
                                    add("LocalBinaryDependent", exportToKotlin = true)
                                },
                            )
                            """.trimIndent(),
                    ),
                ).withKotlinSources(
                    KotlinSource.of(
                        imports = listOf("LocalBinaryDependent.LocalBinaryDependent"),
                    ),
                ).withSwiftSources(
                    SwiftSource.of(
                        content =
                            """
                            import Foundation
                            import LocalBinaryDependent
                            @objc public class MySwiftClass: NSObject {
                            }
                            """.trimIndent(),
                    ),
                ).build()

        // When
        val result =
            GradleBuilder
                .runner(fixture.gradleProject.rootDir, "build")
                .build()

        // Then
        assertThat(result).task(":library:build").succeeded()
        assertThat(result).task(":library:SwiftPackageConfigAppleDummyResolveSwiftPackage").succeeded()
    }
}
