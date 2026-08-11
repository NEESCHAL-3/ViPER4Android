# Nees OEM Defaults v2 startup race fix

Root cause: MainViewModel could load/save an older per-device flat profile while
ViperService was installing OEM defaults. That stale state could win and become
the SHM state seen by the AIDL driver.

v2 uses one shared Mutex-protected initializer. Both Service and MainViewModel
call it before reading DataStore/device state. Marker is written last.

Marker: nees_oem_defaults_v2
