# Embow TV

Android TV application for streaming video content with Netflix-style UI.

## Features

- Android TV native app with D-pad navigation
- ExoPlayer (Media3) for video playback
- Device pairing with QR code
- Secure token storage with EncryptedSharedPreferences
- 20-second heartbeat sync with remote server
- Command handling: REFRESH, PAUSE, RESUME, REBOOT, NOW_PLAYING

## Architecture

- **Pairing Flow**: Generate device_id (UUID), display pairing_code and QR
- **Authentication**: Claim device_token via Supabase Edge Function
- **Playlist**: Fetch and play videos via get-device-playlist
- **Heartbeat**: 20s interval calls to tv-heartbeat with command execution

## Building

### Local Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease  # Requires keystore
./gradlew bundleRelease    # Requires keystore
```

### GitHub Actions

The project includes CI/CD workflow that:
- Builds APK and AAB on every push
- Uses secrets for release signing:
  - `KEYSTORE_BASE64`: Base64-encoded keystore file
  - `KEYSTORE_PASSWORD`: Keystore password
  - `KEY_ALIAS`: Key alias
  - `KEY_PASSWORD`: Key password

To setup secrets:
1. Encode your keystore: `base64 -i your-keystore.jks | pbcopy`
2. Add to GitHub repository secrets
3. Push to trigger workflow

## Requirements

- Android 5.0+ (API 21)
- Android TV device
- Supabase project with Edge Functions deployed

## Edge Functions

Required Supabase Edge Functions:
- `claim-device-token`: Exchange pairing code for device token
- `get-device-playlist`: Get video playlist for device
- `tv-heartbeat`: Send status and receive commands (verifyJWT=true)

## Configuration

Update Supabase URL and anon key in:
- `ApiClient.kt` constructor calls in Activities and Services
