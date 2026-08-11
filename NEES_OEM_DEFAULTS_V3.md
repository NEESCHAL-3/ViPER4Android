# Nees OEM Defaults v3

Fixes the only cold-boot mismatch found in v2.

Bass gain is constrained by the controller to 50..1000. v1/v2 used 22 for
speaker and 28 for headphones, so device-profile JSON deserialization clamped
speaker bass to 50 after reboot. v3 makes the factory values valid and explicit:
- Speaker bass gain: 60 (serialized DSP value 0.60)
- Headphone bass gain: 70 (serialized DSP value 0.70)

All other OEM values are unchanged. Marker: nees_oem_defaults_v3.
