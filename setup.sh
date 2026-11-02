#!/bin/bash
# Dijalankan di CI setelah `npx cap add android`:
# pasang plugin galeri + izin, nomor versi, dan penandatanganan APK tetap.
set -e
D=android/app/src/main
cp GalleryPlugin.java MainActivity.java $D/java/com/iqbal/galeri/

python3 - <<'PY'
p='android/app/src/main/AndroidManifest.xml'
s=open(p).read()
add='''    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
'''
if 'READ_MEDIA_IMAGES' not in s:
    s=s.replace('</manifest>',add+'</manifest>')
open(p,'w').write(s)
PY

# nomor versi = nomor build (agar aplikasi tahu ada versi lebih baru)
BUILD="${APP_BUILD:-1}"
cat >> android/app/build.gradle <<GRADLE

android {
    defaultConfig {
        versionCode $BUILD
        versionName "1.$BUILD"
    }
}
GRADLE

# tanda tangan tetap (wajib agar APK baru bisa menimpa yang lama)
if [ -n "$KS_PATH" ] && [ -f "$KS_PATH" ]; then
cat >> android/app/build.gradle <<GRADLE

android {
    signingConfigs {
        galeri {
            storeFile file('$KS_PATH')
            storePassword '$KS_PASS'
            keyAlias '$KS_ALIAS'
            keyPassword '$KS_PASS'
        }
    }
    buildTypes {
        debug {
            signingConfig signingConfigs.galeri
        }
    }
}
GRADLE
echo "Penandatanganan tetap: AKTIF"
else
echo "PERINGATAN: KEYSTORE_B64 belum diisi, APK memakai kunci sementara (tidak bisa jadi update)."
fi
