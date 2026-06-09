import DummyFramework
import Foundation

/// A trivial @objc class that links against the transitively-resolved binary `DummyFramework`
/// xcframework, so that building (and exporting) this product requires the remote binary artifact
/// to be present in the shared scratch `artifacts/` directory.
@objc public class LocalBinaryDependent: NSObject {
    @objc public func dummyFrameworkVersion() -> Double {
        DummyFrameworkVersionNumber
    }
}
