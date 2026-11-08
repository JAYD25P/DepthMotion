# DepthMotion

Android photo widget, depth viewer and live wallpaper with smooth gyroscope parallax.

## Three ways to see your photos move
- Widget: rotating photos on the home screen. Motion mode uses a foreground service. On Android 12 and newer it sends tiny margin updates instead of a new bitmap each frame.
- Depth Viewer: full-screen, runs at display refresh rate. Swipe to change photo, double tap to switch Depth and Flat, tap to hide controls.
- Live wallpaper: the only route to true 60 fps motion on the home screen. Double tap the wallpaper to change photo.

## Motion engine
- Gyroscope rate is integrated with a leak, so no drift builds up and the photo settles back to centre.
- A critically damped spring smooths sensor jitter.
- Accelerometer fallback for phones without a gyroscope. Idle float keeps the photo alive when the phone is still.
- Depth style warps a mesh using an estimated depth map (centre near, brighter areas slightly nearer). Flat style slides the whole photo.
- Shine highlight follows tilt. EXIF rotation is respected. Sensors pause while the screen is off.

## Platform note
Widgets are host-rendered RemoteViews, so launchers limit how often they redraw. The viewer and the wallpaper are not limited.

## Build
Open in Android Studio or run `gradle :app:assembleDebug`. The GitHub Actions workflow uploads a debug APK artifact.

Minimum SDK 26. Target SDK 35.
