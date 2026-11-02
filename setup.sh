#!/bin/bash
# Dijalankan di CI setelah `npx cap add android`: pasang plugin galeri + izin.
set -e
D=android/app/src/main
cp GalleryPlugin.java MainActivity.java $D/java/com/iqbal/galeri/
python3 - <<'PY'
p='android/app/src/main/AndroidManifest.xml'
s=open(p).read()
add='''    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
'''
if 'READ_MEDIA_IMAGES' not in s:
    s=s.replace('</manifest>',add+'</manifest>')
open(p,'w').write(s)
PY
