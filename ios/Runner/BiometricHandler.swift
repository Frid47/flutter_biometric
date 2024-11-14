import LocalAuthentication
import Security

@objc class BiometricHandler: NSObject {
    private let service = "com.your.app.biometric"
    private let accessGroup = "your.app.group" // Replace with your app group

    @objc func storeInKeychain(_ arguments: NSDictionary, result: @escaping FlutterResult) {
        guard let secret = arguments["secret"] as? String,
              let accessControl = arguments["accessControl"] as? String else {
            result(FlutterError(code: "INVALID_ARGUMENTS",
                              message: "Invalid arguments",
                              details: nil))
            return
        }

        // Create access control
        var error: Unmanaged<CFError>?
        let access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            accessControl == "biometryAny" ? .biometryAny : .biometryCurrentSet,
            &error
        )

        guard error == nil else {
            result(FlutterError(code: "ACCESS_CONTROL_ERROR",
                              message: error?.takeRetainedValue().localizedDescription ?? "Unknown error",
                              details: nil))
            return
        }

        // Prepare query
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup,
            kSecValueData as String: secret.data(using: .utf8)!,
            kSecUseAuthenticationUI as String: kSecUseAuthenticationUIAllow,
            kSecAttrAccessControl as String: access!,
        ]

        // Delete existing item first
        SecItemDelete(query as CFDictionary)

        // Add new item
        let status = SecItemAdd(query as CFDictionary, nil)
        result(status == errSecSuccess)
    }

    @objc func retrieveFromKeychain(_ arguments: NSDictionary, result: @escaping FlutterResult) {
        guard let reason = arguments["reason"] as? String else {
            result(FlutterError(code: "INVALID_ARGUMENTS",
                              message: "Invalid reason",
                              details: nil))
            return
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true,
            kSecUseOperationPrompt as String: reason,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess,
              let data = item as? Data,
              let secret = String(data: data, encoding: .utf8) else {
            result(nil)
            return
        }

        result(secret)
    }

    @objc func removeFromKeychain(_ result: @escaping FlutterResult) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccessGroup as String: accessGroup,
        ]

        let status = SecItemDelete(query as CFDictionary)
        result(status == errSecSuccess || status == errSecItemNotFound)
    }
}
