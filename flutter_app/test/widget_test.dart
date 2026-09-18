import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_app/main.dart';

void main() {
  testWidgets('App smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const WifiChargerApp());
    expect(find.text('WiFi Battery Charger'), findsOneWidget);
  });
}
