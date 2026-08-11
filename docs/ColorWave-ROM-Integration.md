# ColorWave ROM-Native Integration

ColorWave is the ROM-native audio controller and AIDL DSP stack used by this project.

## Final architecture

```text
ColorOS Settings / QS
        |
        v
ColorWave privileged controller (com.nees.audio)
        |
        v
AIDL + V4MS shared-memory control/status
        |
        v
libv4a_aidl.so
        |
        v
AudioFlinger / MTK Audio HAL
        |
        v
stock FS19xx SmartPA + stock device calibration
```

The ColorOS Settings launcher integration is intentionally not automated by the future-base port tool.

## Core ROM files

```text
/system_ext/priv-app/ColorWave/ColorWave.apk
/system/etc/init/init.colorwave.rc
/system/etc/init/colorwave-shm-bootstrap.sh

/vendor/lib/soundfx/libv4a_aidl.so
/vendor/lib64/soundfx/libv4a_aidl.so
```

The target base's own audio-effect XML and vendor SELinux CIL are patched in place. Do not replace whole target-base XML/policy files with files from an older ROM.

## AIDL effect IDs

Implementation UUID:

```text
90380da3-8536-4744-a6a3-5731970e640f
```

Type UUID:

```text
7261676f-6d75-7369-6364-28e2fd3ac39e
```

Registration:

```xml
<library name="v4a_aidl" path="libv4a_aidl.so"/>
<effect name="v4a_standard_aidl"
        library="v4a_aidl"
        uuid="90380da3-8536-4744-a6a3-5731970e640f"
        type="7261676f-6d75-7369-6364-28e2fd3ac39e"/>
```

## V4MS shared memory

Current development/ROM path:

```text
/data/local/tmp/v4a/shm_status.bin   256 bytes
/data/local/tmp/v4a/shm_params.bin  4096 bytes
/data/local/tmp/v4a/shm_bulk.bin    4096 bytes
```

The ROM bootstrap is created by system init. ColorWave maps all three files directly. The MTK audio HAL maps the same control plane.

The controller is AIDL-only. Legacy `AudioEffect.getParameter()` status queries are not used for the ColorWave engine-status UI; status comes from the V4MS status block.

## SELinux

SELinux remains Enforcing.

The reusable port tool derives the target ROM's current versioned `shell_data_file_<version>` type and adds only the required rules for:

```text
hal_audio_default
mtk_hal_audio
```

Do not make the ROM globally permissive. Do not add broad `priv_app` access to unrelated Oplus vendor properties just to silence audit noise.

## FS19xx / hardware safety

Do not modify or transplant:

- FS19xx calibration
- SmartPA hardware gain
- thermal protection thresholds
- vendor speaker calibration
- complete `audio_param` directories from another base

ColorWave sits upstream of the stock MTK/FS19xx device path.

## Clean-flash behavior

On a clean flash:

1. PackageManager discovers `/system_ext/priv-app/ColorWave/ColorWave.apk`.
2. Android creates ColorWave CE/DE app-data containers with privileged-app labels.
3. system init creates the V4MS SHM files.
4. ColorWave starts and maps the three SHM blocks.
5. AudioFlinger loads the AIDL effect.
6. Factory ColorWave defaults initialize normally.

No KernelSU audio module, ADB setup, manual relabel, or `/data/app` APK is required.

## Dirty development installs

If an old development APK remains as an `UPDATED_SYSTEM_APP`, use the `adopt-system` mode of the future-base port script. It snapshots ColorWave CE/DE data, removes only the `/data/app` system-update copy, falls back to the baked system APK, restores data/labels, and verifies the native system app.

## Future-base workflow

Capture once from a known-good ColorWave ROM:

```bash
bash Nees-ColorWave-FutureBase-PortKit.sh capture
```

On a new base:

```bash
bash Nees-ColorWave-FutureBase-PortKit.sh bake
```

Manually reboot once, then:

```bash
bash Nees-ColorWave-FutureBase-PortKit.sh verify
```

If verification reports `UPDATED_SYSTEM_APP=YES`:

```bash
bash Nees-ColorWave-FutureBase-PortKit.sh adopt-system
bash Nees-ColorWave-FutureBase-PortKit.sh verify
```

ColorOS Settings integration is handled manually.

## Current known-good captured hashes

```text
ColorWave.apk
de5da93792fa7957679516e3e0b1c1b293120b750f62e0e69fa146d971f16917

32-bit libv4a_aidl.so
d4122d64b43cf28db17a18d24c5f39320c214b42a5bfab650665b049b3aecec3

64-bit libv4a_aidl.so
bf9c1f38eeb1a795a59c9c256008f8b6b92e54c41df78a4d5ae1943014c81750

init.colorwave.rc
35e9d5bb66aa535efceaf5b96a9436ff0f7f27745ca348fbd0845287cf3d2175

colorwave-shm-bootstrap.sh
0798521ac0bff9e151e6da7db44f6df4a62d25f82e5cd5d228a87557174f6c51
```

These hashes document the current known-good capture; future verified ColorWave revisions may intentionally change them.
