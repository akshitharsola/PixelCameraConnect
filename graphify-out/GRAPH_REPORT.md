# Graph Report - .  (2026-09-12)

## Corpus Check
- Corpus is ~15,554 words - fits in a single context window. You may not need a graph.

## Summary
- 204 nodes · 215 edges · 17 communities detected
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 11 edges (avg confidence: 0.75)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Camera Mode & Capture Control|Camera Mode & Capture Control]]
- [[_COMMUNITY_Camera Accessibility Service Core|Camera Accessibility Service Core]]
- [[_COMMUNITY_Settings Management|Settings Management]]
- [[_COMMUNITY_Wear Tile Layout Builder|Wear Tile Layout Builder]]
- [[_COMMUNITY_App Updates & Status Dashboard|App Updates & Status Dashboard]]
- [[_COMMUNITY_Watch Message Handling|Watch Message Handling]]
- [[_COMMUNITY_Watch Remote Activity Lifecycle|Watch Remote Activity Lifecycle]]
- [[_COMMUNITY_MIUI Fork Design Rationale|MIUI Fork Design Rationale]]
- [[_COMMUNITY_Main Activity Camera Launch|Main Activity Camera Launch]]
- [[_COMMUNITY_Wear Message Listener Service|Wear Message Listener Service]]
- [[_COMMUNITY_Tile Button Construction|Tile Button Construction]]
- [[_COMMUNITY_Project Style Conventions|Project Style Conventions]]
- [[_COMMUNITY_Debug Log Export|Debug Log Export]]
- [[_COMMUNITY_Mobile Architecture Doc|Mobile Architecture Doc]]
- [[_COMMUNITY_Mobile Module Overview|Mobile Module Overview]]
- [[_COMMUNITY_Mobile App Icon|Mobile App Icon]]
- [[_COMMUNITY_Wear App Icon|Wear App Icon]]

## God Nodes (most connected - your core abstractions)
1. `CameraControlService` - 24 edges
2. `SettingsManager` - 21 edges
3. `RemoteActivity` - 20 edges
4. `MainActivity` - 11 edges
5. `CameraRemoteTileService` - 11 edges
6. `SettingsActivity.onCreate` - 11 edges
7. `UpdateChecker` - 10 edges
8. `TileActionActivity` - 8 edges
9. `WearMessageListenerService` - 6 edges
10. `MainActivity.checkForUpdates` - 6 edges

## Surprising Connections (you probably didn't know these)
- `RemoteActivity (doc reference)` --references--> `RemoteActivity`  [EXTRACTED]
  README.md → wear/src/main/java/com/cameraremote/wear/RemoteActivity.kt
- `TileActionActivity (doc reference)` --references--> `TileActionActivity`  [EXTRACTED]
  README.md → wear/src/main/java/com/cameraremote/wear/TileActionActivity.kt
- `Known Issues / Notes` --references--> `TileActionActivity`  [EXTRACTED]
  SKILL.md → wear/src/main/java/com/cameraremote/wear/TileActionActivity.kt
- `CameraRemoteTileService (doc reference)` --references--> `CameraRemoteTileService`  [EXTRACTED]
  README.md → wear/src/main/java/com/cameraremote/wear/CameraRemoteTileService.kt
- `Wear Module (SKILL.md section)` --references--> `RemoteActivity`  [EXTRACTED]
  SKILL.md → wear/src/main/java/com/cameraremote/wear/RemoteActivity.kt

## Hyperedges (group relationships)
- **Watch Command Dispatch Flow** — wearmessagelistenerservice_onmessagereceived, cameracontrolservice_instance, cameracontrolservice_handlecommand, cameracontrolservice_cameracontrolservice [EXTRACTED 0.90]
- **Settings-Driven Capture Behavior** — settingsmanager_settingsmanager, cameracontrolservice_docapture, cameracontrolservice_tapshutterfallback, cameracontrolservice_capture, settingsactivity_settingsactivity [INFERRED 0.85]
- **Phone-Watch Status/Settings Sync** — cameracontrolservice_sendstatustowatch, wearmessagelistenerservice_sendstatustowatch, settingsactivity_syncwatchsettings, cameracontrolservice_path_status [INFERRED 0.80]
- **Wear Tile click-to-command forwarding pattern** — cameraremotetileservice_button, cameraremotetileservice_tile_action_class, tileactionactivity_tileactionactivity, tileactionactivity_sendcommand [INFERRED 0.85]
- **Watch-side command dispatch via Wearable MessageClient** — remoteactivity_sendcommand, tileactionactivity_sendcommand, logcollectorservice_onmessagereceived, skill_communication_protocol [INFERRED 0.80]
- **MIUI-driven fork design rationale cluster** — readme_why_fork_exists, readme_miui_accessibility_limitation, readme_coordinate_based_gestures, readme_app_tracked_mode_state, readme_symmetric_mode_scrolling, readme_camera_open_guard [EXTRACTED 0.90]

## Communities

### Community 0 - "Camera Mode & Capture Control"
Cohesion: 0.11
Nodes (28): CameraControlService.capture, CameraControlService.captureWithTimer, CameraControlService.doCapture, CameraControlService.handleCommand, CameraControlService.instance (companion), CameraControlService.onServiceConnected, CameraControlService.openCamera, PATH_STATUS constant (+20 more)

### Community 1 - "Camera Accessibility Service Core"
Cohesion: 0.09
Nodes (1): CameraControlService

### Community 2 - "Settings Management"
Cohesion: 0.09
Nodes (2): SettingsActivity, SettingsManager

### Community 3 - "Wear Tile Layout Builder"
Cohesion: 0.1
Nodes (8): CameraRemoteTileService, README Architecture - wear module, CameraRemoteTileService (doc reference), RemoteActivity (doc reference), TileActionActivity (doc reference), Known Issues / Notes, Wear Module (SKILL.md section), TileActionActivity

### Community 4 - "App Updates & Status Dashboard"
Cohesion: 0.11
Nodes (12): MainActivity.checkForUpdates, MainActivity.onCreate, MainActivity.onResume, MainActivity.updateServiceStatus, MainActivity.updateWatchConnection, UpdateChecker.checkForUpdate, UpdateChecker.downloadAndInstall, UpdateChecker.downloadWatchApk (+4 more)

### Community 5 - "Watch Message Handling"
Cohesion: 0.11
Nodes (18): LogCollectorService, LogCollectorService.onMessageReceived, RemoteActivity.cancelCountdown, RemoteActivity.checkConnection, RemoteActivity.heartbeatRunnable, RemoteActivity.loadSyncedSettings, RemoteActivity.onCreate, RemoteActivity.onDataChanged (+10 more)

### Community 6 - "Watch Remote Activity Lifecycle"
Cohesion: 0.11
Nodes (1): RemoteActivity

### Community 7 - "MIUI Fork Design Rationale"
Cohesion: 0.12
Nodes (17): App-tracked mode state (currentModeIndex), Camera-open guard for mode-scroll/switch, CameraControlService (doc reference), Coordinate-based taps/swipes approach, Devin AI (original build assistance), What Was Removed From The Original, How It Works (command flow), MIUI camera accessibility limitations (+9 more)

### Community 8 - "Main Activity Camera Launch"
Cohesion: 0.18
Nodes (1): MainActivity

### Community 9 - "Wear Message Listener Service"
Cohesion: 0.29
Nodes (2): PATH_CAMERA_REMOTE constant, WearMessageListenerService

### Community 10 - "Tile Button Construction"
Cohesion: 0.29
Nodes (7): CameraRemoteTileService.buildLayout, CameraRemoteTileService.button, CameraRemoteTileService.onResourcesRequest, CameraRemoteTileService.onTileRequest, CameraRemoteTileService.openAppButton, REMOTE_ACTIVITY_CLASS constant, TILE_ACTION_CLASS constant

### Community 11 - "Project Style Conventions"
Cohesion: 0.67
Nodes (3): Code style matches NotificationMirror (rationale), NotificationMirror (reference project), CameraRemote Project Overview (SKILL.md)

### Community 12 - "Debug Log Export"
Cohesion: 1.0
Nodes (2): CameraControlService.isRunning, MainActivity.exportDebugLogs

### Community 13 - "Mobile Architecture Doc"
Cohesion: 1.0
Nodes (1): README Architecture - mobile module

### Community 14 - "Mobile Module Overview"
Cohesion: 1.0
Nodes (1): Mobile Module (SKILL.md section)

### Community 15 - "Mobile App Icon"
Cohesion: 1.0
Nodes (1): Mobile App Launcher Icon (camera silhouette design)

### Community 16 - "Wear App Icon"
Cohesion: 1.0
Nodes (1): Wear App Launcher Icon (camera silhouette design)

## Knowledge Gaps
- **32 isolated node(s):** `UpdateChecker.downloadWatchApk`, `UpdateChecker.installApk`, `GITHUB_API_URL constant`, `MainActivity.onCreate`, `MainActivity.updateWatchConnection` (+27 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Debug Log Export`** (2 nodes): `CameraControlService.isRunning`, `MainActivity.exportDebugLogs`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Mobile Architecture Doc`** (1 nodes): `README Architecture - mobile module`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Mobile Module Overview`** (1 nodes): `Mobile Module (SKILL.md section)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Mobile App Icon`** (1 nodes): `Mobile App Launcher Icon (camera silhouette design)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Wear App Icon`** (1 nodes): `Wear App Launcher Icon (camera silhouette design)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CameraControlService` connect `Camera Accessibility Service Core` to `Settings Management`, `App Updates & Status Dashboard`?**
  _High betweenness centrality (0.174) - this node is a cross-community bridge._
- **Why does `SettingsManager` connect `Settings Management` to `Camera Mode & Capture Control`, `Camera Accessibility Service Core`?**
  _High betweenness centrality (0.169) - this node is a cross-community bridge._
- **Why does `MainActivity.updateServiceStatus` connect `App Updates & Status Dashboard` to `Camera Accessibility Service Core`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **What connects `UpdateChecker.downloadWatchApk`, `UpdateChecker.installApk`, `GITHUB_API_URL constant` to the rest of the system?**
  _32 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Camera Mode & Capture Control` be split into smaller, more focused modules?**
  _Cohesion score 0.11 - nodes in this community are weakly interconnected._
- **Should `Camera Accessibility Service Core` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._
- **Should `Settings Management` be split into smaller, more focused modules?**
  _Cohesion score 0.09 - nodes in this community are weakly interconnected._