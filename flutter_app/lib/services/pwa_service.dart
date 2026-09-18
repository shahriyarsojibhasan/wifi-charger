import 'dart:async';
import 'package:flutter/foundation.dart';
import 'dart:js_interop' if (dart.library.io) 'dart:async';

@JS('triggerPwaInstall')
external JSPromise<JSBoolean>? _jsTriggerPwaInstall();

@JS('isPwaInstallable')
external JSBoolean? get _jsIsPwaInstallable;

class PwaService extends ChangeNotifier {
  static final PwaService instance = PwaService._internal();
  PwaService._internal();

  bool get canInstall {
    if (!kIsWeb) return false;
    try {
      return _jsIsPwaInstallable?.toDart ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> promptInstall() async {
    if (!kIsWeb) return false;
    try {
      final promise = _jsTriggerPwaInstall();
      if (promise != null) {
        final result = await promise.toDart;
        notifyListeners();
        return result.toDart;
      }
    } catch (_) {}
    return false;
  }
}
