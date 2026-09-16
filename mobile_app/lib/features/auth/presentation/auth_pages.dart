import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';

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
    title: 'Welcome back',
    subtitle: 'Sign in to continue planning meals.',
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          if (widget.showResetSuccess)
            const _SuccessBanner(
              message: 'Password reset. You can now sign in.',
            ),
          TextFormField(
            controller: _email,
            autofillHints: const [AutofillHints.username, AutofillHints.email],
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(labelText: 'Email'),
            validator: _emailValidator,
          ),
          const SizedBox(height: 16),
          PasswordField(
            controller: _password,
            onSubmitted: _submit,
            validator: _loginPasswordValidator,
            helperText: 'Enter your password',
          ),
          const SizedBox(height: 24),
          SubmitButton(
            label: 'Sign in',
            submitting: _submitting,
            onPressed: _submit,
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: () => context.go('/auth/forgot-password'),
            child: const Text('Forgot password?'),
          ),
          TextButton(
            onPressed: () => context.go('/auth/register'),
            child: const Text('Create an account'),
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
    title: 'Create your account',
    subtitle: 'We will ask you to verify your email before you sign in.',
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
            decoration: const InputDecoration(labelText: 'Display name'),
            validator: (value) {
              final trimmed = value?.trim() ?? '';
              if (trimmed.isEmpty) {
                return 'Enter a display name.';
              }
              if (trimmed.length > 100) {
                return 'Display name must be 100 characters or fewer.';
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
            decoration: const InputDecoration(labelText: 'Email'),
            validator: _emailValidator,
          ),
          const SizedBox(height: 16),
          PasswordField(controller: _password, onSubmitted: _submit),
          const SizedBox(height: 24),
          SubmitButton(
            label: 'Create account',
            submitting: _submitting,
            onPressed: _submit,
          ),
          TextButton(
            onPressed: () => context.go('/auth/login'),
            child: const Text('Already have an account? Sign in'),
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
      setState(
        () => _error = 'The verification link is invalid or incomplete.',
      );
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.sessionController.verifyEmail(token);
      if (mounted) {
        setState(() => _success = 'Email verified. You can now sign in.');
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
        setState(
          () => _success =
              'If the account needs verification, a new email has been sent.',
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
    title: 'Verify your email',
    subtitle:
        'Open the verification link from your email, or request another one.',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (_error != null) ProblemBanner(message: _error!),
        if (_success != null) _SuccessBanner(message: _success!),
        if (widget.token != null) ...[
          SubmitButton(
            label: 'Verify email',
            submitting: _submitting,
            onPressed: _verify,
          ),
          const SizedBox(height: 20),
        ],
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          decoration: const InputDecoration(labelText: 'Email for a new link'),
        ),
        const SizedBox(height: 16),
        OutlinedButton(
          onPressed: _submitting ? null : _resend,
          child: const Text('Resend verification email'),
        ),
        TextButton(
          onPressed: () => context.go('/auth/login'),
          child: const Text('Back to sign in'),
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
    title: 'Reset your password',
    subtitle: 'Enter your email and we will send instructions if an account is eligible.',
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          if (_submitted)
            const _SuccessBanner(
              message: 'If an account matches this email, reset instructions have been sent.',
            ),
          TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: 'Email'),
            validator: _emailValidator,
          ),
          const SizedBox(height: 24),
          SubmitButton(
            label: 'Send reset instructions',
            submitting: _submitting,
            onPressed: _submit,
          ),
          TextButton(
            onPressed: () => context.go('/auth/login'),
            child: const Text('Back to sign in'),
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
      setState(
        () => _error = 'The password reset link is invalid or incomplete.',
      );
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
    title: 'Choose a new password',
    subtitle: 'Use at least 12 characters and no more than 72 UTF-8 bytes.',
    child: Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null) ProblemBanner(message: _error!),
          PasswordField(controller: _password, onSubmitted: _submit),
          const SizedBox(height: 24),
          SubmitButton(
            label: 'Reset password',
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
    this.helperText = '12-72 UTF-8 bytes',
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
      labelText: 'Password',
      helperText: widget.helperText,
      suffixIcon: IconButton(
        tooltip: _obscure ? 'Show password' : 'Hide password',
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
    return 'Enter your email.';
  }
  if (email.length > 320 ||
      !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email)) {
    return 'Enter a valid email.';
  }
  return null;
}

String? _passwordValidator(String? value) {
  final password = value ?? '';
  if (password.trim().isEmpty) {
    return 'Enter a password.';
  }
  if (password.length < 12) {
    return 'Password must contain at least 12 characters.';
  }
  if (utf8.encode(password).length > 72) {
    return 'Password must be 72 UTF-8 bytes or fewer.';
  }
  return null;
}

String? _loginPasswordValidator(String? value) {
  if ((value ?? '').trim().isEmpty) {
    return 'Enter a password.';
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
    return 'Signed in, but your account could not be loaded. Please try again.';
  }
  if (error case ApiHttpException(statusCode: 401) when login) {
    return 'Email or password is incorrect.';
  }
  if (resetPassword && error is ApiHttpException) {
    final problem = error.problem;
    if (problem != null) {
      return switch (problem.code) {
        'INVALID_PASSWORD_RESET_TOKEN' =>
          'This password reset link is invalid or expired.',
        'INVALID_PASSWORD' => 'Choose a password with at least 12 characters and no more than 72 UTF-8 bytes.',
        _ when problem.detail != null => problem.detail!,
        _ => 'The password reset request could not be completed.',
      };
    }
    return 'The password reset request could not be completed.';
  }
  if (error case ApiHttpException(statusCode: 409)) {
    return 'This email address is already registered.';
  }
  if (error case ApiHttpException(problem: final problem?)
      when problem.detail != null) {
    return problem.detail!;
  }
  if (error is ApiTransportException) {
    return 'Unable to reach the service. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}
