$ErrorActionPreference = "Stop"

$projectDir = $PSScriptRoot
$outDir = "$projectDir\build\classes"
$buildDir = "$projectDir\build"
$modsDir = "$projectDir\..\..\minecraft\mods"

Write-Host "=== Building Recipe Book JEI ===" -ForegroundColor Cyan

if (Test-Path $outDir) {
    Remove-Item -Recurse -Force $outDir
}
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$cp = @(
    "E:\12\libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar",
    "E:\12\libraries\net\neoforged\neoforge\21.1.256\neoforge-21.1.256-client.jar",
    "E:\12\libraries\net\neoforged\neoforge\21.1.256\neoforge-21.1.256-universal.jar",
    "E:\12\libraries\net\neoforged\bus\8.0.2\bus-8.0.2.jar",
    "E:\12\libraries\net\neoforged\fancymodloader\loader\4.0.45\loader-4.0.45.jar",
    "d:\PrismLauncher\instances\1.21.1(1)\minecraft\mods\jei-1.21.1-neoforge-19.55.0.432.jar",
    "E:\12\libraries\org\spongepowered\mixin\0.8.5\mixin-0.8.5.jar",
    "E:\12\libraries\org\lwjgl\lwjgl-glfw\3.3.3\lwjgl-glfw-3.3.3.jar",
    "E:\12\libraries\org\apache\logging\log4j\log4j-api\2.22.1\log4j-api-2.22.1.jar",
    "E:\12\libraries\com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar",
    "E:\12\libraries\com\mojang\datafixerupper\8.0.16\datafixerupper-8.0.16.jar"
) -join ";"

$sources = (Get-ChildItem -Path "$projectDir\src\main\java" -Recurse -Filter "*.java").FullName
Write-Host "Compiling $($sources.Count) Java sources..."

javac -proc:none -encoding UTF-8 -d $outDir -cp $cp $sources

Write-Host "Copying resources..."
Copy-Item -Path "$projectDir\src\main\resources\*" -Destination $outDir -Recurse -Force

$jarName = "recipebookjei-1.21.1-1.0.0.jar"
$targetJar = "$buildDir\$jarName"

Write-Host "Packaging jar to $targetJar..."
Push-Location $outDir
jar -cf $targetJar *
Pop-Location

Write-Host "Installing to mods folder: $modsDir..."
Copy-Item $targetJar $modsDir -Force

Write-Host "=== Build & Installation Complete! ===" -ForegroundColor Green
