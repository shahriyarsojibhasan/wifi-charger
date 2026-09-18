import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/localization_service.dart';

class AuthGate extends StatefulWidget {
  final Widget child;
  const AuthGate({super.key, required this.child});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  bool _isLoading = true;
  bool _isUnlocked = false;
  final TextEditingController _passCtrl = TextEditingController();
  bool _obscureText = true;
  String? _errorMessage;

  static const String _masterPass = 'yourashahriyar';

  @override
  void initState() {
    super.initState();
    _checkSavedAuth();
  }

  @override
  void dispose() {
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _checkSavedAuth() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final unlocked = prefs.getBool('volt_unlocked_v1') ?? false;
      if (mounted) {
        setState(() {
          _isUnlocked = unlocked;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _submitPasscode() async {
    final input = _passCtrl.text.trim();
    if (input == _masterPass) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('volt_unlocked_v1', true);
      if (mounted) {
        setState(() {
          _isUnlocked = true;
          _errorMessage = null;
        });
      }
    } else {
      setState(() {
        _errorMessage = LocalizationService.instance.isBengali
            ? 'ভুল পাসওয়ার্ড! দয়া করে সঠিক পাসওয়ার্ড দিন।'
            : 'Incorrect password! Please try again.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        backgroundColor: Color(0xFF080C18),
        body: Center(
          child: CircularProgressIndicator(color: Color(0xFF00E5FF)),
        ),
      );
    }

    if (_isUnlocked) {
      return widget.child;
    }

    final loc = LocalizationService.instance;
    final isBn = loc.isBengali;

    return Scaffold(
      backgroundColor: const Color(0xFF080C18),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
          child: Container(
            constraints: const BoxConstraints(maxWidth: 420),
            padding: const EdgeInsets.all(28),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(28),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF161E32), Color(0xFF0D1322)],
              ),
              border: Border.all(
                color: const Color(0xFF00E5FF).withValues(alpha: 0.3),
                width: 1.5,
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFF00E5FF).withValues(alpha: 0.12),
                  blurRadius: 30,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Lock Icon with glowing halo
                Container(
                  width: 76,
                  height: 76,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: const RadialGradient(
                      colors: [Color(0xFF00E5FF), Color(0xFF0088FF)],
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: const Color(0xFF00E5FF).withValues(alpha: 0.4),
                        blurRadius: 20,
                      ),
                    ],
                  ),
                  child: const Icon(Icons.shield_rounded, color: Colors.black, size: 38),
                ),
                const SizedBox(height: 20),

                Text(
                  isBn ? 'ভোল্টপালস সিকিউরিটি' : 'VoltPulse Security',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  isBn
                      ? 'অ্যাপটি ব্যক্তিগত ও সুরক্ষিত। প্রথমবার প্রবেশের জন্য পাসওয়ার্ড দিন।'
                      : 'This app is private & secured. Enter master passcode to unlock.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white54, fontSize: 12.5, height: 1.4),
                ),
                const SizedBox(height: 26),

                // Passcode input
                TextField(
                  controller: _passCtrl,
                  obscureText: _obscureText,
                  style: const TextStyle(color: Colors.white, fontSize: 15, letterSpacing: 1.5),
                  onSubmitted: (_) => _submitPasscode(),
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.lock_outline, color: Color(0xFF00E5FF), size: 20),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscureText ? Icons.visibility_off : Icons.visibility,
                        color: Colors.white38,
                        size: 20,
                      ),
                      onPressed: () => setState(() => _obscureText = !_obscureText),
                    ),
                    hintText: isBn ? 'মাস্টার পাসওয়ার্ড দিন' : 'Enter master passcode',
                    hintStyle: const TextStyle(color: Colors.white24, fontSize: 13, letterSpacing: 0),
                    filled: true,
                    fillColor: Colors.white.withValues(alpha: 0.05),
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.1)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.12)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: const BorderSide(color: Color(0xFF00E5FF), width: 1.5),
                    ),
                  ),
                ),

                if (_errorMessage != null) ...[
                  const SizedBox(height: 10),
                  Text(
                    _errorMessage!,
                    style: const TextStyle(color: Color(0xFFFF5252), fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                ],

                const SizedBox(height: 22),

                // Unlock Button
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00E676),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 4,
                    ),
                    icon: const Icon(Icons.key_rounded, size: 20),
                    label: Text(
                      isBn ? 'আনলক করুন' : 'Unlock Access',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                    ),
                    onPressed: _submitPasscode,
                  ),
                ),

                const SizedBox(height: 14),
                Text(
                  isBn
                      ? '✓ একবার পাসওয়ার্ড দিলে এই ফোনে আর দ্বিতীয়বার চাইবে না।'
                      : '✓ Once unlocked, this browser will remember access permanently.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white38, fontSize: 11),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
