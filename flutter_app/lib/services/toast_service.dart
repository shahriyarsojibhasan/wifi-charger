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

  void show(String message, {ToastType type = ToastType.info}) {
    _controller.add(ToastMessage(message, type));
  }

  void success(String message) => show(message, type: ToastType.success);
  void error(String message) => show(message, type: ToastType.error);
  void info(String message) => show(message, type: ToastType.info);
  void warning(String message) => show(message, type: ToastType.warning);

  void dispose() => _controller.close();
}
