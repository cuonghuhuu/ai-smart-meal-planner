# Smart Meal Planner mobile app

Flutter client for Android and Web. It includes authentication, food and
ingredient catalogs, profile and preferences, measurements, meal planning, and
Pantry/Fridge inventory.

## Run locally

Start the backend separately, then from `mobile_app/` run:

```sh
flutter pub get
flutter run -d chrome --dart-define=APP_ENV=development --dart-define=API_BASE_URL=http://localhost:8080
```

For Android, use a backend URL reachable by the emulator or device:

```sh
flutter run --dart-define=APP_ENV=development --dart-define=API_BASE_URL=https://your-backend-host
```

`APP_ENV` defaults to `development`, and `API_BASE_URL` defaults to
`http://localhost:8080`. The Android emulator sees the host at `10.0.2.2`,
but this app does not currently declare a cleartext HTTP exception. Use an
HTTPS backend URL for Android unless its development network policy is
configured separately.
See `lib/app/config/app_config.dart` for the compile-time configuration.

## Checks

```sh
flutter analyze
flutter test
```
