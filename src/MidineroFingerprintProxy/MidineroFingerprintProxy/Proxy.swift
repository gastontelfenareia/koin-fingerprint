//
//  Proxy.swift
//  MidineroFingerprintProxy
//
//  Created by Gaston Telfeyan on 20/7/22.
//

import Foundation
import FingerprintSDK

@objc(Fingerprint)
public class FingerprintProxy : NSObject {

    @objc
    public static func register(organizationId: String) {
        DeviceFingerprint.register(organizationId: organizationId)
    }
    
    @objc
    public static func register(organizationId: String, url: String) {
        DeviceFingerprint.register(organizationId: organizationId, url: url)
    }
    
    @objc
    public static func profile(sessionId: String) {
        DeviceFingerprint.profile(sessionId: sessionId)
    }
    
//    @objc
//    public static func profile() -> String {
//        DeviceFingerprint.profile()
//    }
    
//    @objc
//    public static func gatherDeviceInformation() -> [String: Any?] {
//        return DeviceFingerprint.gatherDeviceInformation()
//    }
}
