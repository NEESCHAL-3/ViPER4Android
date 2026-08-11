#!/usr/bin/env bash

MODE="${1:-help}"
ARG2="${2:-}"
PKG="com.nees.audio"
UUID="90380da3-8536-4744-a6a3-5731970e640f"
TYPE_UUID="7261676f-6d75-7369-6364-28e2fd3ac39e"

KIT="${COLORWAVE_KIT:-$HOME/Downloads/Nees-ColorWave-PortKit}"
HOST_LOG_DIR="$HOME/Downloads"

need_adb() {
    if ! command -v adb >/dev/null 2>&1; then
        printf 'ERROR: adb not found\n'
        return 1
    fi
    if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
        printf 'ERROR: adb device not connected\n'
        return 1
    fi
    if ! adb shell 'su -M -c "id"' 2>/dev/null | grep -q 'uid=0'; then
        printf 'ERROR: root via su -M is unavailable\n'
        return 1
    fi
    return 0
}

show_help() {
    cat <<EOF
===== NEES COLORWAVE FUTURE-BASE PORT KIT =====

First time, on the CURRENT known-good ColorWave ROM:
  bash $(basename "$0") capture

After flashing a FUTURE base:
  bash $(basename "$0") bake

Then manually reboot once and run:
  bash $(basename "$0") verify

Only if verify reports UPDATED_SYSTEM_APP=YES:
  bash $(basename "$0") adopt-system
  bash $(basename "$0") verify

Modes:
  capture       Save the known-good ColorWave APK/native/init files into the laptop kit.
  bake          Bake ColorWave into a new base. Settings.apk is NEVER touched.
  verify        Read-only post-reboot verification.
  adopt-system  Remove a /data/app updated-system copy while preserving ColorWave data.
  help          Show this guide.

Kit directory:
  $KIT
EOF
}

capture_mode() {
    need_adb || return

    TS="$(date +%Y%m%d-%H%M%S)"
    LOG="$HOST_LOG_DIR/Nees-ColorWave-PortKit-capture-$TS.txt"

    mkdir -p \
        "$KIT/app" \
        "$KIT/native/lib/soundfx" \
        "$KIT/native/lib64/soundfx" \
        "$KIT/init" \
        "$KIT/reference" \
        "$KIT/meta"

    {
        printf '===== COLORWAVE CAPTURE =====\n'
        printf 'KIT=%s\n' "$KIT"
        printf 'SETTINGS_CAPTURED=NO\n\n'

        printf '===== DEVICE =====\n'
        adb shell 'getprop ro.product.device; getprop ro.build.fingerprint; getprop ro.build.version.incremental' 2>/dev/null | tr -d '\r'

        printf '\n===== SOURCE FILES =====\n'
        adb shell 'su -M -c "
          ls -lZ \
            /system_ext/priv-app/ColorWave/ColorWave.apk \
            /vendor/lib/soundfx/libv4a_aidl.so \
            /vendor/lib64/soundfx/libv4a_aidl.so \
            /system/etc/init/init.colorwave.rc \
            /system/etc/init/colorwave-shm-bootstrap.sh 2>/dev/null
        "' 2>/dev/null | tr -d '\r'

        root_pull() {
            REMOTE="$1"
            LOCAL="$2"
            mkdir -p "$(dirname "$LOCAL")"
            rm -f "$LOCAL"

            if adb exec-out "su -M -c 'cat $REMOTE'" > "$LOCAL" 2>/dev/null &&
               [ -s "$LOCAL" ]; then
                printf 'ROOT_PULL=YES %s -> %s\n' "$REMOTE" "$LOCAL"
                return 0
            fi

            rm -f "$LOCAL"
            printf 'ROOT_PULL=NO %s\n' "$REMOTE"
            return 1
        }

        printf '\n===== PULL CORE =====\n'
        CORE_OK=1

        root_pull /system_ext/priv-app/ColorWave/ColorWave.apk \
            "$KIT/app/ColorWave.apk" || CORE_OK=0
        root_pull /vendor/lib/soundfx/libv4a_aidl.so \
            "$KIT/native/lib/soundfx/libv4a_aidl.so" || CORE_OK=0
        root_pull /vendor/lib64/soundfx/libv4a_aidl.so \
            "$KIT/native/lib64/soundfx/libv4a_aidl.so" || CORE_OK=0
        root_pull /system/etc/init/init.colorwave.rc \
            "$KIT/init/init.colorwave.rc" || CORE_OK=0
        root_pull /system/etc/init/colorwave-shm-bootstrap.sh \
            "$KIT/init/colorwave-shm-bootstrap.sh" || CORE_OK=0

        printf '\n===== PULL REFERENCE ONLY =====\n'
        for P in \
            /vendor/etc/audio_effects.xml \
            /vendor/etc/audio_effects_config.xml \
            /system/etc/audio_effects.xml \
            /vendor/etc/selinux/vendor_sepolicy.cil \
            /vendor/etc/selinux/plat_sepolicy_vers.txt
        do
            NAME="$(printf '%s' "$P" | sed 's#^/##; s#/#__#g')"
            if root_pull "$P" "$KIT/reference/$NAME"; then
                printf 'REFERENCE=%s\n' "$P"
            else
                printf 'REFERENCE_SKIPPED=%s\n' "$P"
            fi
        done

        printf '\n===== CORE HASHES =====\n'
        rm -f "$KIT/meta/core.sha256"

        if [ "$CORE_OK" = "1" ]; then
            (
                cd "$KIT" || return
                sha256sum \
                    app/ColorWave.apk \
                    native/lib/soundfx/libv4a_aidl.so \
                    native/lib64/soundfx/libv4a_aidl.so \
                    init/init.colorwave.rc \
                    init/colorwave-shm-bootstrap.sh \
                    > meta/core.sha256
                cat meta/core.sha256
            )
        else
            printf 'CORE_HASHES=SKIPPED_CAPTURE_INCOMPLETE\n'
        fi

        printf '\n===== CAPTURE INFO =====\n'
        {
            printf 'captured_at=%s\n' "$(date -Iseconds)"
            printf 'package=%s\n' "$PKG"
            printf 'uuid=%s\n' "$UUID"
            printf 'type_uuid=%s\n' "$TYPE_UUID"
            printf 'settings_managed_by_script=no\n'
            adb shell 'printf "device=%s\nfingerprint=%s\nincremental=%s\n" \
                "$(getprop ro.product.device)" "$(getprop ro.build.fingerprint)" "$(getprop ro.build.version.incremental)"' \
                2>/dev/null | tr -d '\r'
        } > "$KIT/meta/capture-info.txt"
        cat "$KIT/meta/capture-info.txt"

        printf '\n===== RESULT =====\n'
        REQUIRED=1
        [ "${CORE_OK:-0}" = "1" ] || REQUIRED=0
        for F in \
            "$KIT/app/ColorWave.apk" \
            "$KIT/native/lib/soundfx/libv4a_aidl.so" \
            "$KIT/native/lib64/soundfx/libv4a_aidl.so" \
            "$KIT/init/init.colorwave.rc" \
            "$KIT/init/colorwave-shm-bootstrap.sh" \
            "$KIT/meta/core.sha256"
        do
            [ -s "$F" ] || REQUIRED=0
        done

        if [ "$REQUIRED" = "1" ] &&
           (cd "$KIT" && sha256sum -c meta/core.sha256 >/dev/null 2>&1); then
            printf 'COLORWAVE_PORTKIT_CAPTURE=PASS\n'
            printf 'FUTURE_BASE_READY=YES\n'
            printf 'SETTINGS_UNTOUCHED=YES\n'
        else
            printf 'COLORWAVE_PORTKIT_CAPTURE=FAIL\n'
        fi
    } | tee "$LOG"

    printf '\nHOST_LOG=%s\n' "$LOG"
}

prepare_target_files() {
    WORK="$1"
    mkdir -p "$WORK/xml" "$WORK/policy"

    TARGETS="/vendor/etc/audio_effects.xml /vendor/etc/audio_effects_config.xml /system/etc/audio_effects.xml"

    for P in $TARGETS; do
        if adb shell "su -M -c '[ -f $P ] && echo YES || echo NO'" 2>/dev/null | grep -q YES; then
            NAME="$(printf '%s' "$P" | sed 's#^/##; s#/#__#g')"
            adb pull "$P" "$WORK/xml/$NAME" >/dev/null 2>&1
        fi
    done

    adb pull /vendor/etc/selinux/vendor_sepolicy.cil \
        "$WORK/policy/vendor_sepolicy.cil" >/dev/null 2>&1
    adb pull /vendor/etc/selinux/plat_sepolicy_vers.txt \
        "$WORK/policy/plat_sepolicy_vers.txt" >/dev/null 2>&1

    python3 - "$WORK" "$UUID" "$TYPE_UUID" <<'PY'
from pathlib import Path
import re, sys

work = Path(sys.argv[1])
uuid = sys.argv[2]
type_uuid = sys.argv[3]

for p in (work / "xml").glob("*"):
    s = p.read_text(errors="replace")

    s = re.sub(
        r'\s*<library\b(?=[^>]*\bname="v4a_(?:re|aidl)")[^>]*/>\s*',
        '\n',
        s,
        flags=re.S,
    )
    s = re.sub(
        r'\s*<effect\b(?=[^>]*\bname="v4a_standard_(?:re|aidl)")[^>]*/>\s*',
        '\n',
        s,
        flags=re.S,
    )

    lib = '        <library name="v4a_aidl" path="libv4a_aidl.so"/>'
    eff = (
        '        <effect name="v4a_standard_aidl" library="v4a_aidl" '
        f'uuid="{uuid}" type="{type_uuid}"/>'
    )

    if "</libraries>" not in s or "</effects>" not in s:
        raise SystemExit(f"ERROR: expected XML sections missing in {p}")

    s = s.replace("</libraries>", lib + "\n    </libraries>", 1)
    s = s.replace("</effects>", eff + "\n    </effects>", 1)
    p.write_text(s)

vers_path = work / "policy/plat_sepolicy_vers.txt"
vendor_path = work / "policy/vendor_sepolicy.cil"

if not vers_path.exists() or not vendor_path.exists():
    raise SystemExit("ERROR: target vendor SELinux CIL files missing")

vers = vers_path.read_text(errors="replace").strip()
vt = "shell_data_file_" + vers.replace(".", "_")

policy = vendor_path.read_text(errors="replace")

rules = [
    f"(allow hal_audio_default {vt} (dir (search read open getattr write add_name remove_name rename)))",
    f"(allow hal_audio_default {vt} (file (read write create open getattr setattr unlink rename map)))",
    f"(allow mtk_hal_audio {vt} (dir (search read open getattr write add_name remove_name rename)))",
    f"(allow mtk_hal_audio {vt} (file (read write create open getattr setattr unlink rename map)))",
]

missing = [r for r in rules if r not in policy]
if missing:
    policy = policy.rstrip() + "\n\n; ===== ColorWave AIDL SHM =====\n" + "\n".join(missing) + "\n"
    vendor_path.write_text(policy)

(work / "policy/versioned_type.txt").write_text(vt + "\n")
print("TARGET_SEPOLICY_VERSION=" + vers)
print("TARGET_SHELL_DATA_TYPE=" + vt)
print("TARGET_XML_PATCH=PASS")
print("TARGET_POLICY_PATCH=PASS")
PY
}

bake_mode() {
    need_adb || return

    if [ ! -f "$KIT/meta/core.sha256" ]; then
        printf 'ERROR: ColorWave kit not captured yet: %s\n' "$KIT"
        printf 'Run capture on the known-good ROM first.\n'
        return
    fi

    if ! (cd "$KIT" && sha256sum -c meta/core.sha256); then
        printf 'ERROR: ColorWave kit hash verification failed\n'
        return
    fi

    TS="$(date +%Y%m%d-%H%M%S)"
    LOG="$HOST_LOG_DIR/Nees-ColorWave-PortKit-bake-$TS.txt"
    WORK="/tmp/Nees-ColorWave-PortKit-$TS"
    STAGE="/data/local/tmp/colorwave-portkit-$TS"

    mkdir -p "$WORK"

    {
        printf '===== COLORWAVE FUTURE-BASE BAKE =====\n'
        printf 'KIT=%s\n' "$KIT"
        printf 'WORK=%s\n' "$WORK"
        printf 'SETTINGS_UNTOUCHED=YES\n\n'

        printf '===== TARGET PRECHECK =====\n'
        adb shell 'su -M -c "
          echo SELINUX=$(getenforce)
          echo BUILD=$(getprop ro.build.fingerprint)
          echo SDK=$(getprop ro.build.version.sdk)
          echo VENDOR_POLICY_VERSION=$(cat /vendor/etc/selinux/plat_sepolicy_vers.txt 2>/dev/null)
          echo AUDIO_HAL_PID=$(pidof android.hardware.audio.service-aidl.mediatek 2>/dev/null)
          echo AUDIO_HAL_DOMAIN=$(pidof android.hardware.audio.service-aidl.mediatek 2>/dev/null | xargs -r ps -AZ -p 2>/dev/null | tail -1 | awk \"{print \\\$1}\")
        "' 2>/dev/null | tr -d '\r'

        prepare_target_files "$WORK"

        printf '\n===== PREPARED TARGET FILES =====\n'
        find "$WORK" -maxdepth 3 -type f -printf '%p\n' | sort

        printf '\n===== STAGE =====\n'
        adb shell "su -M -c 'mkdir -p $STAGE'" >/dev/null 2>&1

        adb push "$KIT/app/ColorWave.apk" "$STAGE/ColorWave.apk" >/dev/null
        adb push "$KIT/native/lib/soundfx/libv4a_aidl.so" "$STAGE/libv4a_aidl_32.so" >/dev/null
        adb push "$KIT/native/lib64/soundfx/libv4a_aidl.so" "$STAGE/libv4a_aidl_64.so" >/dev/null
        adb push "$KIT/init/init.colorwave.rc" "$STAGE/init.colorwave.rc" >/dev/null
        adb push "$KIT/init/colorwave-shm-bootstrap.sh" "$STAGE/colorwave-shm-bootstrap.sh" >/dev/null

        for P in "$WORK/xml/"*; do
            [ -f "$P" ] || continue
            adb push "$P" "$STAGE/$(basename "$P")" >/dev/null
        done

        adb push "$WORK/policy/vendor_sepolicy.cil" "$STAGE/vendor_sepolicy.cil" >/dev/null
        adb push "$WORK/policy/versioned_type.txt" "$STAGE/versioned_type.txt" >/dev/null

        cat > "$WORK/device-bake.sh" <<'DEVICE'
#!/system/bin/sh

STAGE="$1"
UUID="$2"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="/data/adb/Nees/colorwave-portkit/$STAMP"

PASS=1

mount_for() {
    P="$1"
    BEST=""
    while read DEV MNT TYPE OPT REST; do
        case "$P" in
            "$MNT"|"$MNT"/*)
                if [ ${#MNT} -gt ${#BEST} ]; then
                    BEST="$MNT"
                fi
                ;;
        esac
    done < /proc/mounts
    [ -n "$BEST" ] && echo "$BEST" || echo /
}

is_rw() {
    M="$1"
    awk -v m="$M" '$2==m {print $4; exit}' /proc/mounts |
        tr ',' '\n' | grep -q '^rw$'
}

rw_mount() {
    M="$1"
    mount -o remount,rw "$M" 2>/dev/null
    is_rw "$M"
}

ro_mount() {
    M="$1"
    mount -o remount,ro "$M" 2>/dev/null
}

backup_one() {
    P="$1"
    if [ -e "$P" ] || [ -L "$P" ]; then
        D="$BACKUP$(dirname "$P")"
        mkdir -p "$D"
        cp -a "$P" "$D/"
        echo "$P" >> "$BACKUP/existing-files.txt"
    else
        echo "$P" >> "$BACKUP/new-files.txt"
    fi
}

copy_one() {
    SRC="$1"
    DST="$2"
    MODE="$3"
    mkdir -p "$(dirname "$DST")"
    cp -f "$SRC" "$DST"
    chown 0:0 "$DST"
    chmod "$MODE" "$DST"
    restorecon -F "$DST" 2>/dev/null
}

echo "===== BAKE PRECHECK ====="
echo "BACKUP=$BACKUP"
echo "SETTINGS_UNTOUCHED=YES"
echo "SELINUX=$(getenforce)"

[ "$(getenforce 2>/dev/null)" = "Enforcing" ] || {
    echo "ERROR=SELINUX_NOT_ENFORCING"
    PASS=0
}

for F in \
    "$STAGE/ColorWave.apk" \
    "$STAGE/libv4a_aidl_32.so" \
    "$STAGE/libv4a_aidl_64.so" \
    "$STAGE/init.colorwave.rc" \
    "$STAGE/colorwave-shm-bootstrap.sh" \
    "$STAGE/vendor_sepolicy.cil"
do
    [ -s "$F" ] || {
        echo "ERROR=STAGE_MISSING:$F"
        PASS=0
    }
done

mkdir -p "$BACKUP"

TARGETS="
/system_ext/priv-app/ColorWave/ColorWave.apk
/vendor/lib/soundfx/libv4a_aidl.so
/vendor/lib64/soundfx/libv4a_aidl.so
/system/etc/init/init.colorwave.rc
/system/etc/init/colorwave-shm-bootstrap.sh
/vendor/etc/selinux/vendor_sepolicy.cil
/vendor/etc/audio_effects.xml
/vendor/etc/audio_effects_config.xml
/system/etc/audio_effects.xml
"

for P in $TARGETS; do
    backup_one "$P"
done

echo
echo "===== POLICY PRECHECK ====="
if [ -x /system/bin/secilc ]; then
    VERS="$(cat /vendor/etc/selinux/plat_sepolicy_vers.txt 2>/dev/null | tr -d '\r\n')"
    ARGS=""

    [ -f /system/etc/selinux/plat_sepolicy.cil ] &&
        ARGS="$ARGS /system/etc/selinux/plat_sepolicy.cil"

    for F in \
        /system/etc/selinux/mapping/"$VERS".cil \
        /system/etc/selinux/mapping/"$VERS".compat.cil \
        /system/etc/selinux/mapping/"$VERS".ignore.cil \
        /system_ext/etc/selinux/system_ext_sepolicy.cil \
        /system_ext/etc/selinux/mapping/"$VERS".cil \
        /system_ext/etc/selinux/mapping/"$VERS".compat.cil \
        /system_ext/etc/selinux/mapping/"$VERS".ignore.cil \
        /product/etc/selinux/product_sepolicy.cil \
        /product/etc/selinux/mapping/"$VERS".cil \
        /product/etc/selinux/mapping/"$VERS".compat.cil \
        /product/etc/selinux/mapping/"$VERS".ignore.cil \
        /vendor/etc/selinux/plat_pub_versioned.cil
    do
        [ -f "$F" ] && ARGS="$ARGS $F"
    done

    ARGS="$ARGS $STAGE/vendor_sepolicy.cil"

    for F in \
        /odm/etc/selinux/odm_sepolicy.cil \
        /vendor/etc/selinux/odm_sepolicy.cil
    do
        [ -f "$F" ] && ARGS="$ARGS $F"
    done

    /system/bin/secilc -m -M true -G -c 30 \
        $ARGS \
        -o "$STAGE/policy.test" \
        -f /dev/null \
        > "$STAGE/secilc.stdout" 2> "$STAGE/secilc.stderr"

    if [ "$?" = "0" ]; then
        echo "SECILC_FULL_POLICY_TEST=PASS"
    else
        echo "SECILC_FULL_POLICY_TEST=FAIL"
        tail -80 "$STAGE/secilc.stderr" 2>/dev/null
        PASS=0
    fi
else
    echo "SECILC_FULL_POLICY_TEST=SKIPPED_NO_SECILC"
fi

if [ "$PASS" = "1" ]; then
    echo
    echo "===== REMOUNT ====="

    SM="$(mount_for /system/etc/init/init.colorwave.rc)"
    SXM="$(mount_for /system_ext/priv-app/ColorWave/ColorWave.apk)"
    VM="$(mount_for /vendor/lib64/soundfx/libv4a_aidl.so)"

    echo "SYSTEM_MOUNT=$SM"
    echo "SYSTEM_EXT_MOUNT=$SXM"
    echo "VENDOR_MOUNT=$VM"

    rw_mount "$SM" && echo "SYSTEM_RW=YES" || {
        echo "SYSTEM_RW=NO"
        PASS=0
    }

    if [ "$SXM" != "$SM" ]; then
        rw_mount "$SXM" && echo "SYSTEM_EXT_RW=YES" || {
            echo "SYSTEM_EXT_RW=NO"
            PASS=0
        }
    else
        echo "SYSTEM_EXT_RW=YES_SHARED"
    fi

    if [ "$VM" != "$SM" ] && [ "$VM" != "$SXM" ]; then
        rw_mount "$VM" && echo "VENDOR_RW=YES" || {
            echo "VENDOR_RW=NO"
            PASS=0
        }
    else
        echo "VENDOR_RW=YES_SHARED"
    fi

    if [ "$PASS" = "1" ]; then
        echo
        echo "===== COPY COLORWAVE ====="

        copy_one "$STAGE/ColorWave.apk" \
            /system_ext/priv-app/ColorWave/ColorWave.apk 0644
        copy_one "$STAGE/libv4a_aidl_32.so" \
            /vendor/lib/soundfx/libv4a_aidl.so 0644
        copy_one "$STAGE/libv4a_aidl_64.so" \
            /vendor/lib64/soundfx/libv4a_aidl.so 0644
        copy_one "$STAGE/init.colorwave.rc" \
            /system/etc/init/init.colorwave.rc 0644
        copy_one "$STAGE/colorwave-shm-bootstrap.sh" \
            /system/etc/init/colorwave-shm-bootstrap.sh 0755
        copy_one "$STAGE/vendor_sepolicy.cil" \
            /vendor/etc/selinux/vendor_sepolicy.cil 0644

        for MAP in \
            "vendor__etc__audio_effects.xml:/vendor/etc/audio_effects.xml" \
            "vendor__etc__audio_effects_config.xml:/vendor/etc/audio_effects_config.xml" \
            "system__etc__audio_effects.xml:/system/etc/audio_effects.xml"
        do
            SRCNAME="${MAP%%:*}"
            DST="${MAP#*:}"
            if [ -f "$STAGE/$SRCNAME" ]; then
                copy_one "$STAGE/$SRCNAME" "$DST" 0644
                echo "PATCHED_CONFIG=$DST"
            fi
        done

        sync

        if [ -d /data/adb/modules/Nees-Audio-AIDL-Baseline ]; then
            touch /data/adb/modules/Nees-Audio-AIDL-Baseline/disable
            echo "OLD_AUDIO_MODULE_DISABLED=YES"
        else
            echo "OLD_AUDIO_MODULE_PRESENT=NO"
        fi
    fi

    ro_mount "$VM"
    [ "$SXM" != "$VM" ] && ro_mount "$SXM"
    [ "$SM" != "$SXM" ] && [ "$SM" != "$VM" ] && ro_mount "$SM"
fi

echo
echo "===== BAKE RESULT ====="
if [ "$PASS" = "1" ]; then
    echo "COLORWAVE_FUTURE_BASE_BAKE=PASS"
    echo "SETTINGS_UNTOUCHED=YES"
    echo "MANUAL_REBOOT_REQUIRED=YES"
    echo "BACKUP=$BACKUP"
else
    echo "COLORWAVE_FUTURE_BASE_BAKE=FAIL"
    echo "DO_NOT_REBOOT_FOR_COLORWAVE_TEST=YES"
    echo "BACKUP=$BACKUP"
fi
DEVICE

        adb push "$WORK/device-bake.sh" "$STAGE/device-bake.sh" >/dev/null
        adb shell "su -M -c 'chmod 0755 $STAGE/device-bake.sh && sh $STAGE/device-bake.sh $STAGE $UUID'" 2>&1

        printf '\n===== HOST RESULT =====\n'
        printf 'SETTINGS_TOUCHED=NO\n'
        printf 'HOST_LOG=%s\n' "$LOG"
    } | tee "$LOG"
}

verify_mode() {
    need_adb || return

    TS="$(date +%Y%m%d-%H%M%S)"
    LOG="$HOST_LOG_DIR/Nees-ColorWave-PortKit-verify-$TS.txt"

    {
        printf '===== COLORWAVE FUTURE-BASE VERIFY =====\n'
        printf 'SETTINGS_UNTOUCHED=YES\n\n'

        adb shell "su -M -c '
          PASS=1
          UUID=\"$UUID\"
          TYPE_UUID=\"$TYPE_UUID\"
          PKG=\"$PKG\"

          echo ===== PACKAGE =====
          pm path \"\$PKG\" 2>/dev/null
          ACTIVE=\$(pm path \"\$PKG\" 2>/dev/null | head -1)
          echo ACTIVE_PATH=\$ACTIVE

          echo \"\$ACTIVE\" | grep -q \"/system_ext/priv-app/ColorWave/ColorWave.apk\" || PASS=0

          if dumpsys package \"\$PKG\" 2>/dev/null | grep -q UPDATED_SYSTEM_APP; then
            echo UPDATED_SYSTEM_APP=YES
            echo ACTION_REQUIRED=RUN_ADOPT_SYSTEM
            PASS=0
          else
            echo UPDATED_SYSTEM_APP=NO
          fi

          echo
          echo ===== NATIVE =====
          ls -lZ /vendor/lib/soundfx/libv4a_aidl.so /vendor/lib64/soundfx/libv4a_aidl.so 2>/dev/null

          echo
          echo ===== INIT_SHM =====
          [ -f /system/etc/init/init.colorwave.rc ] && echo SYSTEM_COLORWAVE_RC=YES || { echo SYSTEM_COLORWAVE_RC=NO; PASS=0; }
          [ -x /system/etc/init/colorwave-shm-bootstrap.sh ] && echo SYSTEM_BOOTSTRAP_HELPER=YES || { echo SYSTEM_BOOTSTRAP_HELPER=NO; PASS=0; }

          if [ -f /data/local/tmp/v4a/.colorwave_rom_bootstrap ]; then
            echo ROM_BOOTSTRAP_MARKER=YES
            cat /data/local/tmp/v4a/.colorwave_rom_bootstrap 2>/dev/null
          else
            echo ROM_BOOTSTRAP_MARKER=NO
            PASS=0
          fi

          S0=\$(stat -c %s /data/local/tmp/v4a/shm_status.bin 2>/dev/null)
          S1=\$(stat -c %s /data/local/tmp/v4a/shm_params.bin 2>/dev/null)
          S2=\$(stat -c %s /data/local/tmp/v4a/shm_bulk.bin 2>/dev/null)
          echo SHM_SIZES=\$S0,\$S1,\$S2
          [ \"\$S0\" = 256 ] || PASS=0
          [ \"\$S1\" = 4096 ] || PASS=0
          [ \"\$S2\" = 4096 ] || PASS=0

          echo
          echo ===== POLICY =====
          VERS=\$(cat /vendor/etc/selinux/plat_sepolicy_vers.txt 2>/dev/null | tr -d \"\\r\\n\")
          VT=shell_data_file_\$(echo \"\$VERS\" | sed \"s/\\./_/g\")
          COUNT=\$(grep -F \"(allow hal_audio_default \$VT\" /vendor/etc/selinux/vendor_sepolicy.cil 2>/dev/null | wc -l)
          COUNT2=\$(grep -F \"(allow mtk_hal_audio \$VT\" /vendor/etc/selinux/vendor_sepolicy.cil 2>/dev/null | wc -l)
          echo COLORWAVE_POLICY_RULES=\$COUNT+\$COUNT2
          [ \"\$COUNT\" -ge 2 ] 2>/dev/null || PASS=0
          [ \"\$COUNT2\" -ge 2 ] 2>/dev/null || PASS=0

          echo
          echo ===== AIDL_CONFIG =====
          GOOD=0
          BAD=0
          for F in /vendor/etc/audio_effects.xml /vendor/etc/audio_effects_config.xml /system/etc/audio_effects.xml; do
            [ -f \"\$F\" ] || continue
            if grep -q \"name=\\\"v4a_aidl\\\"\" \"\$F\" &&
               grep -q \"uuid=\\\"\$UUID\\\"\" \"\$F\" &&
               grep -q \"type=\\\"\$TYPE_UUID\\\"\" \"\$F\"; then
              GOOD=\$((GOOD+1))
            fi
            if grep -qE \"v4a_re|v4a_standard_re\" \"\$F\"; then
              BAD=\$((BAD+1))
            fi
          done
          echo GOOD_AIDL_CONFIGS=\$GOOD
          echo STALE_LEGACY_CONFIGS=\$BAD
          [ \"\$GOOD\" -ge 1 ] 2>/dev/null || PASS=0
          [ \"\$BAD\" = 0 ] || PASS=0

          echo
          echo ===== AUDIOFLINGER =====
          if dumpsys media.audio_flinger 2>/dev/null | grep -q \"\$UUID\"; then
            echo AUDIOFLINGER_COLORWAVE_UUID=YES
          else
            echo AUDIOFLINGER_COLORWAVE_UUID=NO
            PASS=0
          fi

          echo
          echo ===== APP_SHM =====
          PID=\$(pidof \"\$PKG\" 2>/dev/null)
          echo APP_PID=\$PID
          if [ -n \"\$PID\" ]; then
            MAPS=\$(grep -E \"/data/local/tmp/v4a/(shm_status|shm_params|shm_bulk)\" /proc/\$PID/maps 2>/dev/null)
            echo \"\$MAPS\"
            MC=\$(echo \"\$MAPS\" | grep -c \"/data/local/tmp/v4a/\" 2>/dev/null)
            echo APP_SHM_MAP_COUNT=\$MC
            [ \"\$MC\" -ge 3 ] 2>/dev/null || PASS=0
          else
            echo APP_SHM_MAP_COUNT=0
            PASS=0
          fi

          echo
          echo ===== SELINUX =====
          echo SELINUX=\$(getenforce)
          [ \"\$(getenforce 2>/dev/null)\" = Enforcing ] || PASS=0

          echo
          echo ===== MODULE =====
          if [ -d /data/adb/modules/Nees-Audio-AIDL-Baseline ] &&
             [ ! -f /data/adb/modules/Nees-Audio-AIDL-Baseline/disable ]; then
            echo OLD_AUDIO_MODULE_ACTIVE=YES
            PASS=0
          else
            echo OLD_AUDIO_MODULE_ACTIVE=NO
          fi

          echo
          echo ===== COLORWAVE_AVC =====
          AVC=\$(dmesg 2>/dev/null |
            grep -Ei \"avc:.*denied.*(colorwave|v4a|shm_status|shm_params|shm_bulk|shell_data_file|com.nees.audio)\" |
            grep -v \"vendor_oplus_prop\" |
            tail -80)
          if [ -n \"\$AVC\" ]; then
            echo \"\$AVC\"
            echo COLORWAVE_RELEVANT_AVC=YES
            PASS=0
          else
            echo COLORWAVE_RELEVANT_AVC=NO
          fi

          echo
          echo ===== RESULT =====
          if [ \"\$PASS\" = 1 ]; then
            echo COLORWAVE_FUTURE_BASE_VERIFY=PASS
            echo COLORWAVE_ROM_NATIVE=YES
            echo KERNELSU_AUDIO_MODULE_REQUIRED=NO
            echo SETTINGS_UNTOUCHED=YES
          else
            echo COLORWAVE_FUTURE_BASE_VERIFY=FAIL
          fi
        '" 2>&1

        printf '\nHOST_LOG=%s\n' "$LOG"
    } | tee "$LOG"
}

adopt_system_mode() {
    need_adb || return

    TS="$(date +%Y%m%d-%H%M%S)"
    LOG="$HOST_LOG_DIR/Nees-ColorWave-PortKit-adopt-system-$TS.txt"

    {
        printf '===== COLORWAVE ADOPT BAKED SYSTEM APP =====\n'
        printf 'Use only when verify says UPDATED_SYSTEM_APP=YES.\n'
        printf 'SETTINGS_UNTOUCHED=YES\n\n'

        adb shell "su -M -c '
          PKG=\"$PKG\"
          SYSAPK=/system_ext/priv-app/ColorWave/ColorWave.apk
          STAMP=\$(date +%Y%m%d-%H%M%S)
          BK=/data/adb/Nees/colorwave-adopt-system/\$STAMP
          PASS=1

          if ! dumpsys package \"\$PKG\" 2>/dev/null | grep -q UPDATED_SYSTEM_APP; then
            echo UPDATED_SYSTEM_APP_BEFORE=NO
            echo NOTHING_TO_ADOPT=YES
            PASS=0
          else
            echo UPDATED_SYSTEM_APP_BEFORE=YES
          fi

          [ -f \"\$SYSAPK\" ] || {
            echo SYSTEM_APK_PRESENT=NO
            PASS=0
          }

          CE=/data/user/0/\$PKG
          DE=/data/user_de/0/\$PKG

          if [ \"\$PASS\" = 1 ]; then
            echo BACKUP=\$BK
            am force-stop \"\$PKG\" >/dev/null 2>&1
            sleep 1

            mkdir -p \"\$BK\"
            [ -d \"\$CE\" ] && cp -a \"\$CE\" \"\$BK/CE\"
            [ -d \"\$DE\" ] && cp -a \"\$DE\" \"\$BK/DE\"
            sync
            echo DATA_SNAPSHOT=YES

            echo
            echo ===== REMOVE DATA APP UPDATE =====
            cmd package uninstall-system-updates \"\$PKG\" 2>&1
            sleep 3

            ACTIVE=\$(pm path \"\$PKG\" 2>/dev/null | head -1)
            echo ACTIVE_PATH_AFTER=\$ACTIVE
            echo \"\$ACTIVE\" | grep -q \"/system_ext/priv-app/ColorWave/ColorWave.apk\" || PASS=0

            if dumpsys package \"\$PKG\" 2>/dev/null | grep -q UPDATED_SYSTEM_APP; then
              echo UPDATED_SYSTEM_APP_AFTER=YES
              PASS=0
            else
              echo UPDATED_SYSTEM_APP_AFTER=NO
            fi
          fi

          if [ \"\$PASS\" = 1 ]; then
            am start -n \"\$PKG/com.nees.audio.ui.MainActivity\" >/dev/null 2>&1
            sleep 3
            am force-stop \"\$PKG\" >/dev/null 2>&1
            sleep 1

            [ -d \"\$CE\" ] || mkdir -p \"\$CE\"
            [ -d \"\$DE\" ] || mkdir -p \"\$DE\"

            [ -d \"\$BK/CE\" ] && cp -a \"\$BK/CE/.\" \"\$CE/\"
            [ -d \"\$BK/DE\" ] && cp -a \"\$BK/DE/.\" \"\$DE/\"

            restorecon -RF \"\$CE\" 2>/dev/null
            restorecon -RF \"\$DE\" 2>/dev/null
            sync
            echo DATA_RESTORE=YES

            am start -n \"\$PKG/com.nees.audio.ui.MainActivity\" >/dev/null 2>&1
            sleep 6

            PID=\$(pidof \"\$PKG\" 2>/dev/null)
            echo APP_PID=\$PID
            [ -n \"\$PID\" ] || PASS=0
          fi

          echo
          echo ===== RESULT =====
          if [ \"\$PASS\" = 1 ]; then
            echo COLORWAVE_ADOPT_SYSTEM=PASS
            echo COLORWAVE_DATA_PRESERVED=YES
            echo SETTINGS_UNTOUCHED=YES
            echo BACKUP=\$BK
          else
            echo COLORWAVE_ADOPT_SYSTEM=NO_CHANGE_OR_FAIL
            echo KEEP_BACKUP=YES
            [ -n \"\$BK\" ] && echo BACKUP=\$BK
          fi
        '" 2>&1

        printf '\nHOST_LOG=%s\n' "$LOG"
    } | tee "$LOG"
}

case "$MODE" in
    capture)
        capture_mode
        ;;
    bake)
        bake_mode
        ;;
    verify)
        verify_mode
        ;;
    adopt-system)
        adopt_system_mode
        ;;
    help|-h|--help)
        show_help
        ;;
    *)
        printf 'Unknown mode: %s\n\n' "$MODE"
        show_help
        ;;
esac
