import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({
    super.key,
    required this.sessionController,
    this.showResetSuccess = false,
  });

  final SessionController sessionController;
  final bool showResetSuccess;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  String? _error;
  var _submitting = false;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.login(_email.text.trim(), _password.text);
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error, login: true));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageShell(
    title: AppStrings.welcomeBack,
    subtitle: AppStrings.signInSubtitle,
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          if (widget.showResetSuccess)
            const _SuccessBanner(message: AppStrings.passwordResetSuccess),
          TextFormField(
            controller: _email,
            autofillHints: const [AutofillHints.username, AutofillHints.email],
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(labelText: AppStrings.email),
            validator: _emailValidator,
          ),
          const SizedBox(height: 16),
          PasswordField(
            controller: _password,
            onSubmitted: _submit,
            validator: _loginPasswordValidator,
            helperText: AppStrings.enterPassword,
          ),
          const SizedBox(height: 24),
          SubmitButton(
            label: AppStrings.signIn,
            submitting: _submitting,
            onPressed: _submit,
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => context.go('/auth/forgot-password'),
            child: const Text(AppStrings.forgotPassword),
          ),
          TextButton(
            onPressed: () => context.go('/auth/register'),
            child: const Text(AppStrings.createAccountLink),
          ),
        ],
      ),
    ),
  );
}

class RegistrationPage extends StatefulWidget {
  const RegistrationPage({super.key, required this.sessionController});
  final SessionController sessionController;

  @override
  State<RegistrationPage> createState() => _RegistrationPageState();
}

class _RegistrationPageState extends State<RegistrationPage> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _displayName = TextEditingController();
  final _password = TextEditingController();
  String? _error;
  var _submitting = false;

  @override
  void dispose() {
    _email.dispose();
    _displayName.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.register(
        email: _email.text.trim(),
        password: _password.text,
        displayName: _displayName.text.trim(),
      );
      if (mounted) {
        context.go(
          '/auth/verify-email?email=${Uri.encodeComponent(_email.text.trim())}',
        );
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageShell(
    title: AppStrings.createYourAccount,
    subtitle: AppStrings.registrationSubtitle,
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          TextFormField(
            controller: _displayName,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.name],
            decoration: const InputDecoration(
              labelText: AppStrings.displayName,
            ),
            validator: (value) {
              final trimmed = value?.trim() ?? '';
              if (trimmed.isEmpty) {
                return AppStrings.displayNameRequired;
              }
              if (trimmed.length > 100) {
                return AppStrings.displayNameTooLong;
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.username, AutofillHints.email],
            decoration: const InputDecoration(labelText: AppStrings.email),
            validator: _emailValidator,
          ),
          const SizedBox(height: 16),
          PasswordField(controller: _password, onSubmitted: _submit),
          const SizedBox(height: 24),
          SubmitButton(
            label: AppStrings.createAccount,
            submitting: _submitting,
            onPressed: _submit,
          ),
          TextButton(
            onPressed: () => context.go('/auth/login'),
            child: const Text(AppStrings.alreadyHaveAccount),
          ),
        ],
      ),
    ),
  );
}

class VerifyEmailPage extends StatefulWidget {
  const VerifyEmailPage({
    super.key,
    required this.sessionController,
    this.token,
    this.email,
  });
  final SessionController sessionController;
  final String? token;
  final String? email;

  @override
  State<VerifyEmailPage> createState() => _VerifyEmailPageState();
}

class _VerifyEmailPageState extends State<VerifyEmailPage> {
  final _email = TextEditingController();
  String? _error;
  String? _success;
  var _submitting = false;

  @override
  void initState() {
    super.initState();
    _email.text = widget.email ?? '';
  }

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final token = widget.token;
    if (_submitting || token == null || !_isVerificationToken(token)) {
      setState(() => _error = AppStrings.invalidVerificationLink);
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.verifyEmail(token);
      if (mounted) {
        setState(() => _success = AppStrings.emailVerified);
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _resend() async {
    final validation = _emailValidator(_email.text);
    if (_submitting || validation != null) {
      setState(() => _error = validation);
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.resendVerification(_email.text.trim());
      if (mounted) {
        setState(() => _success = AppStrings.verificationEmailSent);
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageShell(
    title: AppStrings.verifyYourEmail,
    subtitle: AppStrings.verifyEmailSubtitle,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_error != null) ProblemBanner(message: _error!),
        if (_success != null) _SuccessBanner(message: _success!),
        if (widget.token != null) ...[
          SubmitButton(
            label: AppStrings.verifyYourEmail,
            submitting: _submitting,
            onPressed: _verify,
          ),
          const SizedBox(height: 20),
        ],
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(
            labelText: AppStrings.emailForNewLink,
          ),
        ),
        const SizedBox(height: 16),
        OutlinedButton(
          onPressed: _submitting ? null : _resend,
          child: const Text(AppStrings.resendVerificationEmail),
        ),
        TextButton(
          onPressed: () => context.go('/auth/login'),
          child: const Text(AppStrings.backToSignIn),
        ),
      ],
    ),
  );
}

class ForgotPasswordPage extends StatefulWidget {
  const ForgotPasswordPage({super.key, required this.sessionController});
  final SessionController sessionController;

  @override
  State<ForgotPasswordPage> createState() => _ForgotPasswordPageState();
}

class _ForgotPasswordPageState extends State<ForgotPasswordPage> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  String? _error;
  var _submitted = false;
  var _submitting = false;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.forgotPassword(_email.text.trim());
      if (mounted) {
        setState(() => _submitted = true);
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageShell(
    title: AppStrings.resetYourPassword,
    subtitle: AppStrings.resetPasswordSubtitle,
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          if (_submitted)
            const _SuccessBanner(message: AppStrings.resetInstructionsSent),
          TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: AppStrings.email),
            validator: _emailValidator,
          ),
          const SizedBox(height: 24),
          SubmitButton(
            label: AppStrings.sendResetInstructions,
            submitting: _submitting,
            onPressed: _submit,
          ),
          TextButton(
            onPressed: () => context.go('/auth/login'),
            child: const Text(AppStrings.backToSignIn),
          ),
        ],
      ),
    ),
  );
}

class ResetPasswordPage extends StatefulWidget {
  const ResetPasswordPage({
    super.key,
    required this.sessionController,
    this.token,
  });
  final SessionController sessionController;
  final String? token;

  @override
  State<ResetPasswordPage> createState() => _ResetPasswordPageState();
}

class _ResetPasswordPageState extends State<ResetPasswordPage> {
  final _formKey = GlobalKey<FormState>();
  final _password = TextEditingController();
  String? _error;
  var _submitting = false;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final token = widget.token;
    if (_submitting || token == null || token.isEmpty) {
      setState(() => _error = AppStrings.invalidResetLink);
      return;
    }
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.resetPassword(
        token: token,
        password: _password.text,
      );
      if (mounted) {
        context.go('/auth/login?reset=success');
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = _safeError(error, resetPassword: true));
      }
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AuthPageShell(
    title: AppStrings.chooseNewPassword,
    subtitle: AppStrings.passwordRequirements,
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          PasswordField(controller: _password, onSubmitted: _submit),
          const SizedBox(height: 24),
          SubmitButton(
            label: AppStrings.resetPassword,
            submitting: _submitting,
            onPressed: _submit,
          ),
        ],
      ),
    ),
  );
}

class AuthPageShell extends StatelessWidget {
  const AuthPageShell({
    super.key,
    required this.title,
    required this.subtitle,
    required this.child,
  });
  final String title;
  final String subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(subtitle),
                    const SizedBox(height: 24),
                    child,
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class PasswordField extends StatefulWidget {
  const PasswordField({
    super.key,
    required this.controller,
    required this.onSubmitted,
    this.validator,
    this.helperText = AppStrings.passwordHelper,
  });
  final TextEditingController controller;
  final VoidCallback onSubmitted;
  final String? Function(String?)? validator;
  final String helperText;

  @override
  State<PasswordField> createState() => _PasswordFieldState();
}

class _PasswordFieldState extends State<PasswordField> {
  var _obscure = true;
  @override
  Widget build(BuildContext context) => TextFormField(
    controller: widget.controller,
    obscureText: _obscure,
    enableSuggestions: false,
    autocorrect: false,
    autofillHints: const [AutofillHints.password],
    textInputAction: TextInputAction.done,
    onFieldSubmitted: (_) => widget.onSubmitted(),
    decoration: InputDecoration(
      labelText: AppStrings.password,
      helperText: widget.helperText,
      suffixIcon: IconButton(
        tooltip: _obscure ? AppStrings.showPassword : AppStrings.hidePassword,
        onPressed: () => setState(() => _obscure = !_obscure),
        icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
      ),
    ),
    validator: widget.validator ?? _passwordValidator,
  );
}

class SubmitButton extends StatelessWidget {
  const SubmitButton({
    super.key,
    required this.label,
    required this.submitting,
    required this.onPressed,
  });
  final String label;
  final bool submitting;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => FilledButton(
    onPressed: submitting ? null : onPressed,
    child: submitting
        ? const SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : Text(label),
  );
}

class ProblemBanner extends StatelessWidget {
  const ProblemBanner({super.key, required this.message});
  final String message;
  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Text(
        message,
        style: TextStyle(color: Theme.of(context).colorScheme.error),
      ),
    ),
  );
}

class _SuccessBanner extends StatelessWidget {
  const _SuccessBanner({required this.message});
  final String message;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Text(
      message,
      style: TextStyle(color: Theme.of(context).colorScheme.primary),
    ),
  );
}

String? _emailValidator(String? value) {
  final email = value?.trim() ?? '';
  if (email.isEmpty) {
    return AppStrings.enterEmail;
  }
  if (email.length > 320 ||
      !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email)) {
    return AppStrings.validEmail;
  }
  return null;
}

String? _passwordValidator(String? value) {
  final password = value ?? '';
  if (password.trim().isEmpty) {
    return AppStrings.enterPasswordError;
  }
  if (password.length < 12) {
    return AppStrings.passwordTooShort;
  }
  if (utf8.encode(password).length > 72) {
    return AppStrings.passwordTooLong;
  }
  return null;
}

String? _loginPasswordValidator(String? value) {
  if ((value ?? '').trim().isEmpty) {
    return AppStrings.enterPasswordError;
  }
  return null;
}

bool _isVerificationToken(String token) =>
    RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(token);

String _safeError(
  Object error, {
  bool login = false,
  bool resetPassword = false,
}) {
  if (error case SessionInitializationException()) {
    return AppStrings.sessionUserUnavailable;
  }
  if (error case ApiHttpException(statusCode: 401) when login) {
    return AppStrings.incorrectCredentials;
  }
  if (resetPassword && error is ApiHttpException) {
    final problem = error.problem;
    if (problem != null) {
      return switch (problem.code) {
        'INVALID_PASSWORD_RESET_TOKEN' => AppStrings.invalidResetLink,
        'INVALID_PASSWORD' => AppStrings.invalidPassword,
        _ => AppStrings.requestFailed,
      };
    }
    return AppStrings.requestFailed;
  }
  if (error case ApiHttpException(statusCode: 409)) {
    return AppStrings.emailAlreadyRegistered;
  }
  if (error case ApiHttpException(statusCode: final statusCode)
      when statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  return AppStrings.genericError;
}
