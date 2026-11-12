param([string]$AndroidSdk="$env:LOCALAPPDATA/Android/Sdk",[switch]$SkipBuild)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if(-not $env:JAVA_HOME){throw 'Set JAVA_HOME to a JDK 17+ installation first.'}
if(-not $SkipBuild){
    & ./gradlew.bat :app:test :app:assembleRelease :app:lintRelease --console=plain
    if($LASTEXITCODE -ne 0){throw 'Release verification failed.'}
}
New-Item -ItemType Directory -Path '.keys','dist' -Force | Out-Null
$versionName=((Select-String -LiteralPath 'app/build.gradle' -Pattern "versionName '([^']+)'" | Select-Object -First 1).Matches.Groups[1].Value)
if(-not $versionName){throw 'Cannot read versionName from app/build.gradle.'}
$outputApk="dist/pdf-reader-$versionName-universal.apk"
$keyStore=Join-Path $PSScriptRoot '.keys/pdf-reader-release.jks'
$passwordFile=Join-Path $PSScriptRoot '.keys/store-password.txt'
if(-not(Test-Path -LiteralPath $keyStore)){
    if(Test-Path -LiteralPath $passwordFile){throw 'Password exists without keystore; inspect .keys before regenerating.'}
    $env:PDF_READER_STORE_PASSWORD=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    [IO.File]::WriteAllText($passwordFile,$env:PDF_READER_STORE_PASSWORD)
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $keyStore -alias pdf-reader -keyalg RSA -keysize 3072 -validity 10000 -storepass:env PDF_READER_STORE_PASSWORD -keypass:env PDF_READER_STORE_PASSWORD -dname 'CN=PDF Reader Release, OU=Local Android Application' -noprompt
    if($LASTEXITCODE -ne 0){throw 'Signing key generation failed.'}
}else{$env:PDF_READER_STORE_PASSWORD=[IO.File]::ReadAllText($passwordFile)}
try{
    $tools=Join-Path $AndroidSdk 'build-tools/36.0.0'
    & "$tools/zipalign.exe" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk dist/pdf-reader-aligned.apk
    if($LASTEXITCODE -ne 0){throw 'APK alignment failed.'}
    & "$tools/apksigner.bat" sign --ks $keyStore --ks-key-alias pdf-reader --ks-pass env:PDF_READER_STORE_PASSWORD --key-pass env:PDF_READER_STORE_PASSWORD --out $outputApk dist/pdf-reader-aligned.apk
    if($LASTEXITCODE -ne 0){throw 'APK signing failed.'}
    & "$tools/apksigner.bat" verify --verbose --print-certs $outputApk
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed.'}
    & "$tools/zipalign.exe" -c -p 4 $outputApk
    if($LASTEXITCODE -ne 0){throw 'APK alignment check failed.'}
    Get-FileHash -LiteralPath $outputApk -Algorithm SHA256
}finally{Remove-Item -LiteralPath Env:PDF_READER_STORE_PASSWORD -ErrorAction SilentlyContinue}
