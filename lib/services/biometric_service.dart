// lib/services/biometric_service.dart

import 'dart:io';
import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class BiometricService {
  final _platform = const MethodChannel('com.your.app/biometric_crypto');
  final _secureStorage = const FlutterSecureStorage();

  static const String _biometricEnabledKey = 'biometric_enabled';
  static const String _secretDataKey = 'secret_data';
  static const String _ivKey = 'iv_key';

  // Only check availability, don't authenticate
  Future<bool> isBiometricAvailable() async {
    try {
      if (Platform.isAndroid) {
        // For Android, we only check availability
        final result = await _platform.invokeMethod<String>('checkBiometricSupport');
        return result == 'AVAILABLE';
      } else {
        // For iOS, check using local_auth
        final LocalAuthentication localAuth = LocalAuthentication();
        final canAuthenticateWithBiometrics = await localAuth.canCheckBiometrics;
        final canAuthenticate = await localAuth.isDeviceSupported();
        return canAuthenticateWithBiometrics && canAuthenticate;
      }
    } catch (e) {
      print('Error checking biometric availability: $e');
      return false;
    }
  }

  Future<bool> isBiometricEnabled() async {
    try {
      final enabled = await _secureStorage.read(key: _biometricEnabledKey);
      return enabled == 'true';
    } catch (e) {
      print('Error checking if biometric is enabled: $e');
      return false;
    }
  }

  Future<bool> enableBiometric({
    required String secret,
    String reason = 'Please authenticate to enable biometric login',
  }) async {
    try {
      final isAvailable = await LocalAuthentication().canCheckBiometrics;
      if (!isAvailable) {
        return false;
      }

      if (Platform.isAndroid) {
        final result = await _platform.invokeMethod('encryptWithBiometric', {
          'data': secret,
          'promptTitle': 'Enable Biometric Authentication',
          'promptSubtitle': reason,
        });

        if (result == null) return false;

        await _secureStorage.write(
          key: _secretDataKey,
          value: result['encrypted'],
        );
        await _secureStorage.write(
          key: _ivKey,
          value: result['iv'],
        );
      } else {
        // iOS implementation
        final result = await _platform.invokeMethod('storeInKeychain', {
          'secret': secret,
          'accessControl': 'biometryAny',
        });

        if (result != true) return false;
      }

      await _secureStorage.write(key: _biometricEnabledKey, value: 'true');
      return true;
    } catch (e) {
      print('Error enabling biometric: $e');
      return false;
    }
  }

  Future<String?> getSecureData({
    String reason = 'Please authenticate to access secure data',
  }) async {
    try {
      final isEnabled = await isBiometricEnabled();
      if (!isEnabled) return null;

      if (Platform.isAndroid) {
        final encryptedData = await _secureStorage.read(key: _secretDataKey);
        final iv = await _secureStorage.read(key: _ivKey);

        if (encryptedData == null || iv == null) return null;

        return await _platform.invokeMethod('decryptWithBiometric', {
          'encryptedData': encryptedData,
          'iv': iv,
          'promptTitle': 'Authentication Required',
          'promptSubtitle': reason,
        });
      } else {
        // iOS implementation
        return await _platform.invokeMethod('retrieveFromKeychain', {
          'reason': reason,
        });
      }
    } catch (e) {
      print('Error getting secure data: $e');
      return null;
    }
  }

  Future<bool> disableBiometric() async {
    try {
      if (Platform.isAndroid) {
        await _platform.invokeMethod('removeFromKeystore');
        await _secureStorage.delete(key: _secretDataKey);
        await _secureStorage.delete(key: _ivKey);
      } else {
        await _platform.invokeMethod('removeFromKeychain');
      }

      await _secureStorage.delete(key: _biometricEnabledKey);
      return true;
    } catch (e) {
      print('Error disabling biometric: $e');
      return false;
    }
  }
}