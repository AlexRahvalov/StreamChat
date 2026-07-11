$ErrorActionPreference = "Stop"

$oldPkg = "com.example"
$newPkg = "by.rahvalov.streamchat"
$oldMod = "ExampleMod"
$newMod = "StreamChatMod"
$oldMixin = "ExampleMixin"
$newMixin = "StreamChatMixin"

$targetDir = "d:\GitHub\StreamChat"

# 1. Rename files containing ExampleMod or ExampleMixin
Write-Host "Renaming files..."
$filesToRename = Get-ChildItem -Path $targetDir -Include "*ExampleMod*.java", "*ExampleMixin*.java" -Recurse -File
foreach ($file in $filesToRename) {
    $newName = $file.Name -replace $oldMod, $newMod -replace $oldMixin, $newMixin
    Rename-Item -Path $file.FullName -NewName $newName
    Write-Host ("Renamed " + $file.Name + " to " + $newName)
}

# 2. Update contents of files
Write-Host "Updating file contents..."
$files = Get-ChildItem -Path $targetDir -Include *.java,*.json,*.gradle,*.properties,*.md -Recurse -File
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $changed = $false
    
    if ($content -match "com\.example") {
        $content = $content -replace "com\.example", $newPkg
        $changed = $true
    }
    if ($content -match $oldMod) {
        $content = $content -replace $oldMod, $newMod
        $changed = $true
    }
    if ($content -match $oldMixin) {
        $content = $content -replace $oldMixin, $newMixin
        $changed = $true
    }
    if ($content -match "com/example") {
        $content = $content -replace "com/example", "by/rahvalov/streamchat"
        $changed = $true
    }
    
    if ($changed) {
        Set-Content -Path $file.FullName -Value $content -NoNewline
        Write-Host ("Updated content in " + $file.FullName)
    }
}

# 3. Move directories
Write-Host "Moving directories..."

# Main source
$mainDest = "$targetDir\src\main\java\by\rahvalov\streamchat"
if (Test-Path "$targetDir\src\main\java\com\example") {
    New-Item -ItemType Directory -Force -Path $mainDest | Out-Null
    Move-Item -Path "$targetDir\src\main\java\com\example\*" -Destination $mainDest
    Remove-Item -Path "$targetDir\src\main\java\com" -Recurse -Force
}

# Client source
$clientDest = "$targetDir\src\client\java\by\rahvalov\streamchat"
if (Test-Path "$targetDir\src\client\java\com\example") {
    New-Item -ItemType Directory -Force -Path $clientDest | Out-Null
    Move-Item -Path "$targetDir\src\client\java\com\example\*" -Destination $clientDest
    Remove-Item -Path "$targetDir\src\client\java\com" -Recurse -Force
}

Write-Host "Project renamed successfully."
