import 'dart:async';

enum ToastType { success, error, info, warning }

class ToastMessage {
  final String message;
  final ToastType type;
  final int id;

  ToastMessage(this.message, this.type)
      : id = DateTime.now().millisecondsSinceEpoch;
}

class ToastService {
  ToastService._();
  static final ToastService instance = ToastService._();

  final _controller = StreamController<ToastMessage>.broadcast();
  Stream<ToastMessage> get stream => _controller.stream;

  final Map<String, DateTime> _recentMessages = {};

  void show(String message, {ToastType type = ToastType.info, bool force = false}) {
    if (message.trim().isEmpty) return;
    final now = DateTime.now();
    // Clean entries older than 30s
    _recentMessages.removeWhere((_, time) => now.difference(time).inSeconds > 30);

    if (!force) {
      final lastTime = _recentMessages[message];
      if (lastTime != null && now.difference(lastTime).inSeconds < 15) {
        // Suppress duplicate spam
        return;
      }
    }
    _recentMessages[message] = now;
    _controller.add(ToastMessage(message, type));
  }

  void success(String message, {bool force = false}) =>
      show(message, type: ToastType.success, force: force);
  void error(String message, {bool force = false}) =>
      show(message, type: ToastType.error, force: force);
  void info(String message, {bool force = false}) =>
      show(message, type: ToastType.info, force: force);
  void warning(String message, {bool force = false}) =>
      show(message, type: ToastType.warning, force: force);

  void dispose() => _controller.close();
}
