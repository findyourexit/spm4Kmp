package io.github.frankois944.spmForKmp.definition

import java.io.Serializable

/**
 * Describes a single SwiftPM resource rule to be emitted in the bridge target's `resources:` array
 * inside the generated `Package.swift`.
 *
 * SwiftPM compiles processed/copied resources into a `<Package>_<Target>.bundle` that is placed
 * in the per-target build directory.  The existing [CopiedResourcesFactory][io.github.frankois944.spmForKmp.resources.CopiedResourcesFactory]
 * already discovers `.bundle` files from that directory, so no additional copy-task changes are
 * required once the manifest entry is emitted.
 *
 * Usage (in `build.gradle.kts`):
 * ```kotlin
 * target.swiftPackageConfig(cinteropName = "...") {
 *     bridgeResourceRules(
 *         BridgeResourceRule.Process("Resources"),          // .process("Resources")
 *         BridgeResourceRule.Copy("FixedLayout.json"),      // .copy("FixedLayout.json")
 *         BridgeResourceRule.EmbedInCode("shader.metal"),   // .embedInCode("shader.metal")
 *     )
 * }
 * ```
 */
public sealed class BridgeResourceRule : Serializable {
    internal companion object {
        private const val serialVersionUID: Long = 1
    }

    /**
     * Maps to `.process("<path>")` in the generated `Package.swift`.
     *
     * SwiftPM applies default processing rules appropriate for the resource type
     * (e.g. compiling asset catalogs, copying loose files, etc.).
     *
     * @param path Path relative to the bridge target's `Sources/` directory.
     */
    public data class Process(
        val path: String,
    ) : BridgeResourceRule()

    /**
     * Maps to `.copy("<path>")` in the generated `Package.swift`.
     *
     * The resource is copied verbatim into the bundle without any processing.
     *
     * @param path Path relative to the bridge target's `Sources/` directory.
     */
    public data class Copy(
        val path: String,
    ) : BridgeResourceRule()

    /**
     * Maps to `.embedInCode("<path>")` in the generated `Package.swift`.
     *
     * The resource bytes are embedded directly as a Swift `let` constant.
     *
     * @param path Path relative to the bridge target's `Sources/` directory.
     */
    public data class EmbedInCode(
        val path: String,
    ) : BridgeResourceRule()
}
