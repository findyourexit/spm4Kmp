package io.github.frankois944.spmForKmp.tasks.apple.resolveSwiftPackage

import io.github.frankois944.spmForKmp.SPM_TRACE_NAME
import io.github.frankois944.spmForKmp.SWIFT_PACKAGE_NAME
import io.github.frankois944.spmForKmp.config.PackageDirectoriesConfig
import io.github.frankois944.spmForKmp.definition.PackageRootDefinitionExtension
import io.github.frankois944.spmForKmp.tasks.utils.isTraceEnabled

internal fun ResolveSwiftPackageTask.configureTask(
    swiftPackageEntry: PackageRootDefinitionExtension,
    packageDirectoriesConfig: PackageDirectoriesConfig,
) {
    this.workingDir.set(packageDirectoriesConfig.spmWorkingDir.absolutePath)
    this.packageSwift.set(packageDirectoriesConfig.spmWorkingDir.resolve(SWIFT_PACKAGE_NAME))
    this.packageScratchDir.set(packageDirectoriesConfig.packageScratchDir.absolutePath)
    this.sharedCacheDir.set(packageDirectoriesConfig.sharedCacheDir?.absolutePath)
    this.sharedConfigDir.set(packageDirectoriesConfig.sharedConfigDir?.absolutePath)
    this.sharedSecurityDir.set(packageDirectoriesConfig.sharedSecurityDir?.absolutePath)
    this.swiftBinPath.set(swiftPackageEntry.swiftBinPath)
    this.toolchain.set(swiftPackageEntry.toolchain)
    this.artifactsDir.set(packageDirectoriesConfig.packageScratchDir.resolve("artifacts"))
    this.checkoutsDir.set(packageDirectoriesConfig.packageScratchDir.resolve("checkouts"))
    this.repositoriesDir.set(packageDirectoriesConfig.packageScratchDir.resolve("repositories"))
    this.traceEnabled.set(project.isTraceEnabled)
    this.storedTraceFile.set(
        project.projectDir
            .resolve(SPM_TRACE_NAME)
            .resolve(packageDirectoriesConfig.spmWorkingDir.name)
            .resolve("ResolveSwiftPackageTask.html"),
    )
}
