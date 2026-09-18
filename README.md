# Code Radio for Android

첨부된 **coderadio-on-tray 0.5.2**를 분석해 만든 Android 네이티브 포팅 프로젝트입니다.
원본 ZIP의 commit 식별자는 `c6ab1683f906705a1824faf500bdc62bb61a5212`입니다.

**현재 전달물은 소스 프로젝트입니다. APK 빌드 및 실기기 테스트를 마친 릴리스가 아닙니다.**
제작 환경에 Android SDK/Gradle 배포본이 없고 다운로드가 제한되어 APK는 포함하지 못했습니다.
Android 의존성이 없는 실제 핵심 코드 30개 검사, Java 구문 검사, XML/문자열 참조 검사는 통과했습니다.
상세 결과는 [검증 기록](docs/VALIDATION.md)을 참고하세요.

## 구현된 기능

- 첫 정상 곡 정보 조회 후 자동 재생(기본 켜짐), 재생·일시정지·종료
- 앱 화면과 분리된 Media3 `MediaSessionService` / ExoPlayer 백그라운드 재생
- 알림창·잠금화면 미디어 플레이어: 재생/일시정지, 곡명·아티스트·앨범아트
- 알림의 추가 종료 액션(표시 위치/개수는 Android 버전과 제조사에 따라 다름)
- 원본 v2 API에서 **실제 스트림 URL을 조회**, 128/64kbps 선택 및 저음질 미제공 시 기본 스트림 사용
- 원본과 같은 0–100 볼륨 / 0.75 지수 곡선, 청취자 수, 앨범명, 곡명 누락 보정
- 자동 재생·앨범아트·청취자 표시·볼륨·음질 설정 저장
- 곡 변경 시 기본 이미지로 먼저 전환한 뒤 새 앨범아트 반영, 이전 비동기 응답 무시
- 연결 오류/방송 종료 시 1→2→4→8→16→30초 재연결, 사용자 일시정지 시 취소
- 이어폰 분리 시 일시정지, 오디오 포커스 처리, 한국어/영어 UI

부팅 자동 재생, 데스크톱 트레이 클릭 설정, 데스크톱 릴리스 알림은 Android에 이식하지 않았습니다.
Android 릴리스 저장소가 아직 없으므로 앱 업데이트 알림을 데스크톱 릴리스 피드에 연결하지 않았습니다.
전체 대응표는 [기능 매핑](docs/PORTING.md)에 있습니다.

## APK 만들기 — GitHub Actions

1. ZIP 안의 `coderadio-android` 폴더 내용을 **새 GitHub 저장소의 루트**에 올립니다. `.github` 폴더도 포함합니다.
2. GitHub **Actions → Android APK → Run workflow**를 실행합니다.
3. 단위 테스트·Android Lint·빌드가 모두 통과하면 **Artifacts → CodeRadio-Android-debug**를 내려받습니다.
4. 압축 안의 `app-debug.apk`를 Android 휴대폰으로 옮겨 설치합니다.

이 워크플로는 파일로만 포함되어 있으며, 이 대화에서 원격 실행하거나 저장소에 업로드하지 않았습니다.
기존 데스크톱 저장소의 하위 폴더에 넣는 경우 워크플로의 실행 경로/아티팩트 경로를 해당 폴더에 맞춰야 합니다.

## APK 만들기 — 로컬

요구 사항: **Java 17**, **Python 3.9+**, Android SDK **Platform 35 / Build Tools 35.0.0**, 인터넷 연결.
앱은 **Android 8.0(API 26) 이상**을 대상으로 하며 compile/target SDK는 35입니다.
AGP 8.9.2 / Gradle 8.11.1 / Media3 1.6.1로 버전을 고정했습니다.
빌드 스크립트는 `JAVA_HOME`, `PATH` 순서로 Java 17 이상을 찾고, Windows에서는 필요할 경우
Android Studio에 포함된 JBR도 자동으로 사용합니다.
Android SDK는 `local.properties`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`와 운영체제별 표준 설치 경로에서 찾습니다.

1. Android Studio의 SDK Manager에서 필요한 SDK를 설치합니다.
2. 프로젝트 루트에 `local.properties`를 생성합니다. SDK 경로는 자신의 PC에 맞춥니다.

```properties
# macOS 예시
sdk.dir=/Users/yourname/Library/Android/sdk
# Windows에서는 C:/Users/yourname/AppData/Local/Android/Sdk 형태 사용
```

3. 프로젝트 폴더에서 실행합니다.

```bash
# macOS / Linux
bash build_android.sh

# Windows (명령 프롬프트)
build_android.bat
```

빌드 스크립트는 공식 Gradle 배포본과 공식 SHA-256을 다운로드해 검증하고,
`testDebugUnitTest lintDebug assembleDebug`를 실행합니다.
이미 Gradle 8.11.1이 있으면 `gradle testDebugUnitTest lintDebug assembleDebug`도 가능합니다.

결과: `app/build/outputs/apk/debug/app-debug.apk`

## Android Studio에서 개발

이 ZIP에는 다운로드가 불가능했던 Gradle Wrapper JAR를 가짜 파일로 대체하지 않았습니다.
아래 명령은 검증한 Gradle 배포본으로 **공식 Wrapper 파일을 생성**합니다.

```bash
python3 scripts/gradle.py --setup
# Windows: python scripts\gradle.py --setup
```

그다음 Android Studio에서 프로젝트 폴더를 Open → Gradle Sync → Run 합니다.
Wrapper 생성 후에는 `./gradlew testDebugUnitTest lintDebug assembleDebug`를 사용할 수 있습니다.
배포용 APK는 Android Studio의 **Generate Signed App Bundle or APK**에서 본인의 키로 서명합니다.
디버그 APK와 릴리스 APK의 서명이 다르면 기존 앱 위에 업데이트 설치되지 않습니다.

## 사용법

앱을 열면 기본적으로 방송 정보를 가져온 후 재생합니다. 처음 연결할 때까지 앱을 열어 두세요.
재생이 시작되면 홈으로 나가거나 최근 앱에서 화면을 닫아도 재생 서비스가 음악을 이어갑니다.
알림/잠금화면에서 재생을 제어하고, 알림을 누르면 앱 화면으로 돌아옵니다.
일시정지 후 재생하면 지난 버퍼 대신 현재 라이브 방송에 다시 연결합니다.
`재생 종료하고 닫기`는 재생·재시도·알림을 종료합니다.

Android 13+의 미디어 세션 알림은 일반 알림 권한 예외 대상이므로 별도 `POST_NOTIFICATIONS` 팝업을 요청하지 않습니다.
시스템 알림/잠금화면 표시 설정과 제조사 절전 설정에 따라 표시·백그라운드 유지 동작은 달라질 수 있습니다.
오래 일시정지하거나 OS가 서비스를 종료하면 앱을 다시 열어 재생하세요. 강제 종료 후 자동 부활은 하지 않습니다.
로그인, 위치, 마이크, 외부 저장소 접근 권한은 필요하지 않습니다.

## 구조

| 파일 | 역할 |
|---|---|
| `PlaybackService.java` | 오디오·미디어 세션·알림·재시도·곡 정보 갱신 |
| `MainActivity.java` | 서비스 컨트롤러 연결, 플레이어 화면 및 설정 |
| `NowPlayingParser.java` | 원본 AzuraCast 응답과 스트림 마운트 파싱 |
| `PlaybackIntent.java` | 사용자 재생 의사와 지연 재시도 세대 관리 |
| `RadioRules.java` | 곡명 보정, URL 검증, 볼륨 곡선, 재시도 간격 |
| `RadioHttp.java` / `Artwork.java` | 제한 크기 HTTP 요청, 앨범 이미지 디코딩 |
| `scripts/check_offline.py` | Android SDK 없이 수행 가능한 핵심 회귀 검사 |

## 데이터와 출처

원본과 같은 공개 API:
`https://coderadio-admin-v2.freecodecamp.org/api/nowplaying/coderadio`

재생 중 15초, 일시정지 중 서비스가 살아 있으면 60초 간격으로 조회합니다.
스트림 URL을 추측하거나 고정된 구형 서버 주소로 대체하지 않습니다. 초기 API 조회가 실패하면 재시도합니다.
앨범아트는 HTTPS 요청, 최대 5 MiB 수신, 최대 약 512px 디코딩 후 JPEG 바이트로 세션에 전달합니다.
자체 분석 서버나 광고 SDK는 없고 설정은 기기에만 저장합니다. 방송/이미지 제공 서버에는 일반 HTTP 연결 정보가 전달됩니다.

- [원본 프로젝트](https://github.com/pawprint0706/coderadio-on-tray)
- [Android MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [Media3 릴리스 문서](https://developer.android.com/jetpack/androidx/releases/media3)
- [AGP 8.9 호환성](https://developer.android.com/build/releases/agp-8-9-0-release-notes)

freeCodeCamp와 제휴하지 않은 비공식 앱입니다. 원본 제공 아이콘을 재사용했습니다.
freeCodeCamp 로고의 상표권은 해당 조직에 있습니다. [NOTICE](NOTICE.md)를 참고하세요.
