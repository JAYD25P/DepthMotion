# DepthMotion

DepthMotion is an Android home-screen image widget with a personal photo library, selected-folder discovery, scheduled image rotation, and parallax-style rendering.

## Current implementation
- Android App Widget with resizable 2x2, 4x2 and 4x4 layouts.
- Import individual images through the Android Photo Picker / document picker.
- Grant access to a selected folder tree and discover supported images recursively.
- Sequential or shuffled rotation.
- Rotation intervals supported by Android periodic background scheduling (15 minutes minimum).
- Image framing and parallax intensity preferences.
- Widget tap opens the library/settings screen.

## Platform constraints
Android App Widgets use host-rendered RemoteViews and cannot run a continuous gyroscope/OpenGL render loop. DepthMotion therefore renders a still parallax-framed bitmap on scheduled refreshes and when the user requests refresh. A true continuously sensor-driven effect requires a foreground Activity or live wallpaper, which is intentionally not the primary product.

## Build
Open the project in Android Studio or run `gradle :app:assembleDebug`. The GitHub Actions workflow builds and uploads a debug APK artifact.

Minimum SDK: 26. Target SDK: 35.
