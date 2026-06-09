// swift-tools-version:5.9
import PackageDescription

// A local Swift package whose source product transitively depends on a *remote* binary
// xcframework. This models the failing scenario of upstream issue #294 (a localPackage /
// remotePackageVersion whose dependency graph pulls in `.binaryTarget(url:checksum:)` artifacts),
// which only resolve correctly once the shared scratch `artifacts/` directory is produced before
// any target builds.
let package = Package(
    name: "LocalBinaryDependentPackage",
    products: [
        .library(name: "LocalBinaryDependent", targets: ["LocalBinaryDependent"]),
    ],
    targets: [
        .binaryTarget(
            name: "DummyFramework",
            url: "https://spmforkmp.eu/DummyFrameworkV2.xcframework.zip",
            checksum: "90da1dfbf1b52b647958974002a329a60e291b463fcb69a53e2e42b74ead0a94"
        ),
        .target(
            name: "LocalBinaryDependent",
            dependencies: ["DummyFramework"]
        ),
    ]
)
