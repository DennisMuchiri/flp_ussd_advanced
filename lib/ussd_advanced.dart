/// Run ussd code directly in your application
import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:ussd_advanced/accessibility_event.dart';

class UssdAdvanced {
  static const MethodChannel _channel =
      MethodChannel('method.com.phan_tech/ussd_advanced');
  //Initialize BasicMessageChannel
  static const BasicMessageChannel<String> _basicMessageChannel =
      BasicMessageChannel("message.com.phan_tech/ussd_advanced", StringCodec());
  static const EventChannel _eventChannel =
        EventChannel('event.com.phan_tech/ussd_advanced');

  static Stream<AccessibilityEvent>? _stream;

  static Future<void> sendUssd(
      {required String code, int subscriptionId = 1}) async {
    await _channel.invokeMethod(
        'sendUssd', {"subscriptionId": subscriptionId, "code": code});
  }

  static Future<String?> sendAdvancedUssd(
      {required String code, int subscriptionId = 1}) async {
    final String? response = await _channel
        .invokeMethod('sendAdvancedUssd',
            {"subscriptionId": subscriptionId, "code": code})
        .timeout(const Duration(seconds: 30))
        .catchError((e) {
          throw e;
        });
    return response;
  }

  static Future<String?> multisessionUssd(
      {required String code, int subscriptionId = 1}) async {
    var _codeItem = _CodeAndBody.fromUssdCode(code);
    String response = await _channel.invokeMethod('multisessionUssd', {
          "subscriptionId": subscriptionId,
          "code": _codeItem.code
        }).catchError((e) {
          throw e;
        }) ??
        '';

    if (_codeItem.messages != null) {
      var _res = await sendMultipleMessages(_codeItem.messages!);
      response += "\n$_res";
    }
    return response;
  }

  static Future<void> cancelSession() async {
    await _channel
        .invokeMethod(
      'multisessionUssdCancel',
    )
        .catchError((e) {
      throw e;
    });
  }

  static Future<String?> sendMessage(String message) async {
    var _response = await _basicMessageChannel.send(message).catchError((e) {
      throw e;
    });
    return _response;
  }

  static Future<String?> sendMultipleMessages(List<String> messages) async {
    var _response = "";
    for (var m in messages) {
      var _res = await sendMessage(m);
      _response += "\n$_res";
    }

    return _response;
  }

  static Stream<String?> onEnd() {
    StreamController<String?> _streamController = StreamController<String?>();
    _basicMessageChannel.setMessageHandler((message) async {
      _streamController.add(message);
      return message ?? '';
    });

    return _streamController.stream;
  }

  /// request accessibility permission
  /// it will open the accessibility settings page and return `true` once the permission granted.
  static Future<bool> requestAccessibilityPermission() async {
    try {
      return await _channel
          .invokeMethod('requestAccessibilityPermission');
    } on PlatformException catch (error) {
      log("$error");
      return Future.value(false);
    }
  }

  /// check if accessibility permession is enebaled
  static Future<bool> isAccessibilityPermissionEnabled() async {
    try {
      return await _channel
          .invokeMethod('isAccessibilityPermissionEnabled');
    } on PlatformException catch (error) {
      log("$error");
      return false;
    }
  }

  static Stream<AccessibilityEvent> get accessStream {
    if (Platform.isAndroid) {
      _stream ??=
          _eventChannel.receiveBroadcastStream().map<AccessibilityEvent>(
                (event) => AccessibilityEvent.fromMap(event),
          );
      return _stream!;
    }
    throw Exception("Accessibility API exclusively available on Android!");
  }
}

class _CodeAndBody {
  _CodeAndBody(this.code, this.messages);
  _CodeAndBody.fromUssdCode(String _code) {
    var _removeCode = _code.split('#')[0];
    var items = _removeCode.split("*").toList();

    code = '*${items[1]}#';

    if (items.length > 1) {
      messages = items.sublist(2);
    }
  }
  late String code;
  List<String>? messages;
}
