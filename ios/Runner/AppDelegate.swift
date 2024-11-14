import UIKit
import Flutter

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        let controller : FlutterViewController = window?.rootViewController as! FlutterViewController
        let channel = FlutterMethodChannel(
            name: "com.your.app/biometric_crypto",
            binaryMessenger: controller.binaryMessenger
        )

        let handler = BiometricHandler()

        channel.setMethodCallHandler({
            (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
            switch call.method {
            case "storeInKeychain":
                handler.storeInKeychain(call.arguments as! NSDictionary, result: result)
            case "retrieveFromKeychain":
                handler.retrieveFromKeychain(call.arguments as! NSDictionary, result: result)
            case "removeFromKeychain":
                handler.removeFromKeychain(result)
            default:
                result(FlutterMethodNotImplemented)
            }
        })

        GeneratedPluginRegistrant.register(with: self)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}