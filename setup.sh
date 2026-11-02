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

# ikon aplikasi dari icon.png (jika ada) + layar pembuka hitam
ICON=""
for f in icon.png logo2.jpg; do
  if [ -f "$f" ]; then ICON="$f"; break; fi
done
if [ -n "$ICON" ]; then
export ICON
python3 -c "import PIL" 2>/dev/null || python3 -m pip install -q --break-system-packages pillow
python3 - <<'PY'
import os, glob
from PIL import Image, ImageDraw
res = 'android/app/src/main/res'
src = Image.open(os.environ['ICON']).convert('RGBA')
w0, h0 = src.size
sd = min(w0, h0)
src = src.crop(((w0 - sd) // 2, (h0 - sd) // 2, (w0 - sd) // 2 + sd, (h0 - sd) // 2 + sd))
legacy = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
fore = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
for d, s in legacy.items():
    p = f'{res}/mipmap-{d}'
    os.makedirs(p, exist_ok=True)
    sq = src.resize((s, s), Image.LANCZOS)
    sq.save(f'{p}/ic_launcher.png')
    m = Image.new('L', (s, s), 0)
    ImageDraw.Draw(m).ellipse((0, 0, s - 1, s - 1), fill=255)
    rd = sq.copy()
    rd.putalpha(m)
    rd.save(f'{p}/ic_launcher_round.png')
    f = fore[d]
    inner = int(f * 0.68)
    cv = Image.new('RGBA', (f, f), (0, 0, 0, 0))
    cv.paste(src.resize((inner, inner), Image.LANCZOS), ((f - inner) // 2, (f - inner) // 2))
    cv.save(f'{p}/ic_launcher_foreground.png')
open(f'{res}/values/ic_launcher_background.xml', 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#000000</color>\n</resources>\n')
for sp in glob.glob(f'{res}/drawable*/splash.png'):
    w, h = Image.open(sp).size
    Image.new('RGB', (w, h), (0, 0, 0)).save(sp)
print('Ikon aplikasi: DIPASANG')
PY
else
echo "icon.png / logo2.jpg tidak ada, memakai ikon bawaan."
fi
