# Skyline Weather

Skyline Weather is a native Android weather app for Android 8.0 and later. Start with the Paris forecast, search for a city, or grant approximate location permission to check the weather nearby.

- **Today** shows current weather, the next 24 hours, and details such as wind, humidity, visibility, UV, rain chance, pressure, and sunrise and sunset.
- **Forecast** shows seven days of local weather. Tap a day for more details.
- **Places** stores saved cities on the device. **Map** shows the current location on a pannable map with zoom controls.
- **Settings** stores temperature units and an optional local profile. The app supports guest use. The profile is device-local. Cloud accounts and Google or Facebook sign-in are not included.
- A forecast you've opened successfully can be displayed with an offline label if a refresh fails.

Forecasts and city search are provided by [Open-Meteo](https://open-meteo.com/). Map tiles are provided by [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). Internet access is needed for fresh forecasts, city lookup, and map tiles.

## Build

Open this folder in Android Studio and sync it with Android SDK 36. Run the app module on an Android device or emulator, or use the Gradle wrapper with the Android SDK configured.

```powershell
.\gradlew.bat assembleDebug
```

On Windows, the standalone build script also uses the Android SDK and a JDK. It produces a locally signed APK and verifies its signature.

```powershell
.\Build-Apk.ps1 -Sdk 'C:\path\to\Android\Sdk' -Jdk 'C:\path\to\jdk'
```
