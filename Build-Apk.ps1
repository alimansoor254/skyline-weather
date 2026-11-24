param(
    [string] $Sdk = $env:ANDROID_HOME,
    [string] $Jdk = $env:JAVA_HOME,
    [string] $BuildToolsVersion = "36.1.0",
    [string] $OutputDirectory = (Join-Path $PSScriptRoot ".build")
)

$ErrorActionPreference = "Stop"
if (!$Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (!$Jdk) { throw "Set JAVA_HOME or pass -Jdk with the JDK directory." }
$env:JAVA_HOME = $Jdk
$tools = Join-Path $Sdk "build-tools\$BuildToolsVersion"
$platform = Join-Path $Sdk "platforms\android-36\android.jar"
$source = Join-Path $PSScriptRoot "app\src\main"
$out = (New-Item -ItemType Directory -Force $OutputDirectory).FullName
$classes = (New-Item -ItemType Directory -Force "$out\classes").FullName
$dex = (New-Item -ItemType Directory -Force "$out\dex").FullName

function Invoke-BuildTool([string] $Program, [string[]] $Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program exited with code $LASTEXITCODE" }
}

$javaSources = @(Get-ChildItem "$source\java" -Recurse -Filter *.java | ForEach-Object FullName)
Invoke-BuildTool "$Jdk\bin\javac.exe" (@("-encoding", "UTF-8", "--release", "8", "-classpath", $platform, "-d", $classes) + $javaSources)
Invoke-BuildTool "$Jdk\bin\jar.exe" @("cf", "$out\classes.jar", "-C", $classes, ".")
Invoke-BuildTool "$tools\d8.bat" @("--min-api", "26", "--lib", $platform, "--output", $dex, "$out\classes.jar")
Invoke-BuildTool "$tools\aapt2.exe" @("compile", "--dir", "$source\res", "-o", "$out\resources.zip")
$manifest = (Get-Content "$source\AndroidManifest.xml" -Raw).Replace(
    '<manifest xmlns:android', '<manifest package="app.skyline.weather" xmlns:android')
Set-Content "$out\AndroidManifest.xml" $manifest -Encoding UTF8
Invoke-BuildTool "$tools\aapt2.exe" @("link", "-o", "$out\base.apk", "-I", $platform, "--manifest",
    "$out\AndroidManifest.xml", "--min-sdk-version", "26", "--target-sdk-version", "35",
    "--version-code", "1", "--version-name", "1.0", "$out\resources.zip")
Invoke-BuildTool "$Jdk\bin\jar.exe" @("uf", "$out\base.apk", "-C", $dex, "classes.dex")
Invoke-BuildTool "$tools\zipalign.exe" @("-f", "4", "$out\base.apk", "$out\aligned.apk")
$key = "$out\local-debug.jks"
if (!(Test-Path $key)) {
    Invoke-BuildTool "$Jdk\bin\keytool.exe" @("-genkeypair", "-noprompt", "-keystore", $key,
        "-storepass", "android", "-keypass", "android", "-alias", "skyline-debug",
        "-dname", "CN=Skyline Local Build", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650")
}
$apk = "$out\Skyline-Weather.apk"
Invoke-BuildTool "$tools\apksigner.bat" @("sign", "--ks", $key, "--ks-pass", "pass:android", "--ks-key-alias",
    "skyline-debug", "--out", $apk, "$out\aligned.apk")
Invoke-BuildTool "$tools\apksigner.bat" @("verify", "--verbose", $apk)
Write-Output $apk
