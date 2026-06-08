# MedPull Kiosk - Native Android App

## Project Overview
Native Android tablet application for medical form processing with offline capabilities, multi-language support, and HIPAA compliance.

## Features
- **Multi-language Support**: English, Spanish, Chinese, French, Hindi, Arabic
- **Offline Mode**: Local caching with background sync
- **HIPAA Compliant**: Encrypted storage, audit logging, secure transmission
- **AWS Integration**: Cognito, S3, Textract, Translate
- **AI Assistance**: OpenAI/Claude integration for form help
- **Session Management**: 15-minute timeout with warnings
- **PDF Processing**: Form field extraction and filling

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **Min SDK**: API 29 (Android 10)
- **Target SDK**: API 34
- **Dependency Injection**: Hilt
- **Database**: Room
- **Networking**: Retrofit + OkHttp
- **AWS SDK**: Cognito, S3, Textract, Translate
- **PDF**: PDFBox Android + Android PDF Viewer
- **Security**: EncryptedSharedPreferences

## Project Structure
```
app/src/main/java/com/medpull/kiosk/
├── MedPullKioskApplication.kt
├── di/                      # Dependency Injection
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   └── NetworkModule.kt
├── data/
│   ├── local/              # Room Database
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   └── entities/
│   ├── remote/             # AWS & AI Services
│   ├── models/             # Domain Models
│   └── repository/         # Repositories
├── domain/                 # Use Cases
├── ui/                     # Compose UI
│   ├── theme/
│   ├── components/
│   ├── screens/
│   └── navigation/
├── utils/                  # Utilities
└── security/              # Security & Audit

```

## AWS Configuration
- **Region**: us-east-1
- **User Pool ID**: us-east-1_j8Y6JrLF7
- **Client ID**: 12jt58o6hmamb7hsadcrljgo1j
- **API Endpoint**: https://d40uuum7hj.execute-api.us-east-1.amazonaws.com/prod
- **S3 Bucket**: medpull-hipaa-files-1759818639

## AI Co-Pilot (local Ollama)

The intake flow is driven by a local **Ollama** model (`qwen3.5:9b`) reached over
**Tailscale**, using the native `/api/chat` endpoint with `think:false` and JSON-schema
structured output. It assesses each answer and either accepts silently or steps in
("MedPull Assistant") to rephrase/assist/branch. If the box is unreachable the kiosk
falls back to the deterministic form flow — it never strands a patient.

### App setup
1. Set the endpoint in `local.properties` (per-device, not committed):
   ```
   OLLAMA_BASE_URL=http://<tailnet-host-or-100.x.y.z>:11434
   ```
2. Keep that host in sync with the matching `<domain>` in
   `app/src/main/res/xml/network_security_config.xml` (cleartext HTTP is allowed only
   for that host). If you use a `100.x` Tailscale IP, put that IP in both places.
3. Verify the model tag actually exists on the box: `ollama list` (the public Qwen
   tags are usually `qwen2.5:7b` / `qwen3:8b`; change `Constants.AI.OLLAMA_MODEL` if so).

### Server (Ollama box) setup — out of app scope
The app sends `keep_alive:-1` on every request and fires a warm-up from the Welcome
screen, but as a **backup** the box should also keep the model resident across reboots:
- Run Ollama with `OLLAMA_KEEP_ALIVE=-1`.
- Add a startup warm-up (e.g. a one-shot `/api/chat` load on boot) so the very first
  patient after a reboot doesn't pay a cold start.

### Tuning
Thresholds and the timeout in `Constants.AI` (`COPILOT_CONFIDENCE_THRESHOLD`,
`COPILOT_BRANCH_THRESHOLD`, `COPILOT_TIMEOUT_SECONDS`) are **uncalibrated starting
values**. Calibrate them from the on-device `copilot_audit_logs` table (logged
confidence vs. actual correctness, and observed p95/p99 latency).

> ⚠️ **Testing-phase shortcut:** AI decisions (including patient answers / PHI) are
> logged **on-device only** and are NOT production-ready. See [KNOWN_ISSUES.md](../KNOWN_ISSUES.md).

## Build Instructions

### Prerequisites
- Android Studio Iguana or later
- JDK 17
- Android SDK with API 29-34
- Gradle 8.2+

### Setup
1. Clone the repository
2. Open project in Android Studio
3. Create `local.properties` with SDK path:
   ```
   sdk.dir=/path/to/Android/sdk
   ```
4. Sync Gradle
5. Run on tablet device or emulator (landscape mode)

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on device
./gradlew installDebug
```

## Implementation Progress

### Phase 1: Project Setup ✅ (Completed)
- [x] Create Android project structure
- [x] Configure build.gradle.kts with dependencies
- [x] Set up Hilt dependency injection
- [x] Configure AWS credentials in BuildConfig
- [x] Set landscape-only in AndroidManifest
- [x] Create network_security_config.xml
- [x] Set up Room database schema
- [x] Create base package structure

### Phase 2: Core Infrastructure ✅ (Completed)
- [x] Implement SecureStorageManager
- [x] Implement HipaaAuditLogger
- [x] Implement LocaleManager
- [x] Create string resources (English & Spanish - others pending)
- [x] Implement SessionManager
- [x] Set up Material 3 theme
- [x] Create Constants.kt

### Phase 3: AWS Integration ✅ (Completed)
- [x] Create AwsModule with SDK clients
- [x] Implement CognitoAuthService (sign up, sign in, password reset)
- [x] Implement S3Service (upload, download, presigned URLs)
- [x] Implement TextractService (form field extraction)
- [x] Implement TranslationService (6-language support)
- [x] Implement ApiGatewayService (Lambda invocation)
- [x] Update all repositories with AWS integration
- [x] Add StorageRepository and TranslationRepository

### Phase 4: Database & Offline (Next)
- [ ] Implement background sync with WorkManager
- [ ] Create NetworkMonitor for connectivity
- [ ] Implement offline queue for operations
- [ ] Add conflict resolution
- [ ] Test offline functionality

### Remaining Phases
- Phase 5: Authentication Flow (UI)
- Phase 6: Form Management (UI)
- Phase 7: PDF Viewing & Form Filling
- Phase 8: AI Integration
- Phase 9: Export & PDF Generation
- Phase 10: Auto-Logout & Session
- Phase 11: Testing & Security
- Phase 12: Polish & Deployment

## Key Files Created

### Configuration (8 files)
- `build.gradle.kts` (project & app level)
- `settings.gradle.kts`, `gradle.properties`
- `AndroidManifest.xml`, `network_security_config.xml`
- `proguard-rules.pro`, `themes.xml`

### Core Application (2 files)
- `MedPullKioskApplication.kt`
- `MainActivity.kt`

### Dependency Injection (4 files)
- `AppModule.kt` - App-level dependencies
- `DatabaseModule.kt` - Room database
- `NetworkModule.kt` - Retrofit/OkHttp
- `AwsModule.kt` - AWS SDK clients
- `RepositoryModule.kt` - Repository providers

### Data Layer - Database (9 files)
- `AppDatabase.kt`
- Entities: `UserEntity`, `FormEntity`, `FormFieldEntity`, `AuditLogEntity`
- DAOs: `UserDao`, `FormDao`, `FormFieldDao`, `AuditLogDao`

### Data Layer - AWS Services (5 files)
- `CognitoAuthService.kt` - Authentication
- `S3Service.kt` - File storage
- `TextractService.kt` - Form extraction
- `TranslationService.kt` - Multi-language
- `ApiGatewayService.kt` - Lambda calls

### Data Layer - Repositories (7 files)
- `AuthRepository.kt` - Auth with Cognito
- `FormRepository.kt` - Form management
- `AuditRepository.kt` - Audit logging
- `StorageRepository.kt` - File operations
- `TranslationRepository.kt` - Translation

### Domain Models (4 files)
- `User.kt`
- `Form.kt`, `FormField.kt`, `FormStatus.kt`
- `AuditLog.kt`
- `Language.kt`

### UI Components (7 files)
- Theme: `Color.kt`, `Type.kt`, `Theme.kt`
- Navigation: `NavGraph.kt`, `Screen.kt`
- Screens: `WelcomeScreen.kt`, `WelcomeViewModel.kt`

### Utilities (3 files)
- `Constants.kt`
- `LocaleManager.kt`
- `SessionManager.kt`

### Security (2 files)
- `SecureStorageManager.kt`
- `HipaaAuditLogger.kt`

### Resources (3 files)
- `values/strings.xml` (English - 60+ strings)
- `values-es/strings.xml` (Spanish - 60+ strings)
- 4 more languages pending

## Security Features
- FLAG_SECURE: Prevents screenshots
- EncryptedSharedPreferences for tokens
- Certificate pinning for AWS endpoints
- TLS 1.2+ only (no cleartext)
- Room database encryption (planned)
- Audit logging with S3 sync
- Session timeout (15 minutes)
- HIPAA-compliant storage

## Testing
- Unit tests for ViewModels
- Unit tests for Repositories
- Unit tests for Use Cases
- UI tests for critical flows
- Security testing
- HIPAA compliance testing

## License
Proprietary - MedPull Inc.

## Contact
For questions or support, contact the development team.
