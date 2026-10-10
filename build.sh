#!/usr/bin/env bash
# Builds ThorPad.apk without the Android Gradle plugin, using Ubuntu's packaged tools:
#   sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
# plus, from Maven Central, Robolectric's Android 14 framework jar to compile against and
# Shizuku's AIDL classes (deps/shizuku-aidl-13.1.5.jar):
#   https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/
# (The app also builds normally in Android Studio via the Gradle files in this folder.)
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_ALL=${ANDROID_ALL:-deps/android-all-14-robolectric-10818077.jar}
PLATFORM_JAR=${PLATFORM_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
SHIZUKU_AIDL=${SHIZUKU_AIDL:-deps/shizuku-aidl-13.1.5.jar}  # classes.jar of dev.rikka.shizuku:aidl:13.1.5 (Maven Central)
KEYSTORE=${KEYSTORE:-keystore/thorpad.jks}
SRC=app/src/main

# Fetch the two compile-time jars from Maven Central if they aren't here yet.
fetch() { [ -f "$1" ] || { mkdir -p "$(dirname "$1")" && curl -sSfL -o "$1" "$2"; }; }
fetch "$ANDROID_ALL" https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
if [ ! -f "$SHIZUKU_AIDL" ]; then
    fetch deps/shizuku-aidl-13.1.5.aar https://repo1.maven.org/maven2/dev/rikka/shizuku/aidl/13.1.5/aidl-13.1.5.aar
    unzip -p deps/shizuku-aidl-13.1.5.aar classes.jar > "$SHIZUKU_AIDL"
fi
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/res"

# Ubuntu's aapt only knows Android 6 attributes. android:canPerformGestures (Android 7)
# is compiled under a stand-in boolean attribute and its resource id is swapped back below.
cp -r "$SRC/res/." "$OUT/res/"
sed -i 's/android:canPerformGestures=/android:supportsAssist=/' "$OUT/res/xml/accessibility_config.xml"

aapt package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$OUT/res" \
    -I "$PLATFORM_JAR" -F "$OUT/base.apk"

javac -nowarn --release 8 -encoding UTF-8 \
    -classpath "$ANDROID_ALL:$SHIZUKU_AIDL" -d "$OUT/classes" \
    $(find "$SRC/java" "$OUT/gen" -name '*.java')

rm -rf "$OUT/aidl" && mkdir -p "$OUT/aidl" && (cd "$OUT/aidl" && unzip -q "../../$SHIZUKU_AIDL" 'moe/*')
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes" "$OUT/aidl"

python3 - "$OUT/base.apk" "$OUT/classes.dex" "$OUT/unaligned.apk" <<'PY'
import struct, sys, zipfile
base, dex, out = sys.argv[1:]
SUPPORTS_ASSIST, CAN_PERFORM_GESTURES = 0x010104F0, 0x0101050D

def patch_res_map(data):
    # Binary XML: header, string pool, then RES_XML_RESOURCE_MAP_TYPE (0x0180) chunk.
    pos = struct.unpack_from('<H', data, 2)[0]
    while pos < len(data):
        ctype, hsize, csize = struct.unpack_from('<HHI', data, pos)
        if ctype == 0x0180:
            ids = list(struct.unpack_from('<%dI' % ((csize - hsize) // 4), data, pos + hsize))
            assert ids.count(SUPPORTS_ASSIST) == 1, ids
            # The stand-in must sort last so the swapped id keeps attributes in id order.
            assert max(ids) == SUPPORTS_ASSIST, ids
            ids[ids.index(SUPPORTS_ASSIST)] = CAN_PERFORM_GESTURES
            body = struct.pack('<%dI' % len(ids), *ids)
            return data[:pos + hsize] + body + data[pos + hsize + len(body):]
        pos += csize
    raise SystemExit('resource map not found')

with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, 'w') as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'res/xml/accessibility_config.xml':
            data = patch_res_map(data)
        zout.writestr(item, data)
    zout.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
PY

zipalign -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "No signing key at $KEYSTORE; creating a new one (APKs signed with it won't update older installs)."
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass thorpad -keypass thorpad \
        -alias thorpad -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=ThorPad, O=ThorPad" >/dev/null
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:thorpad --key-pass pass:thorpad \
    --ks-key-alias thorpad --min-sdk-version 31 --out "$OUT/ThorPad.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/ThorPad.apk"
echo "Built $OUT/ThorPad.apk"
