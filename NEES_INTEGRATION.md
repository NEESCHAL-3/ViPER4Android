# Nees audio controller foundation

- Internal package: `com.nees.audio`
- No launcher activity; ColorOS Settings/QS will be the entry points.
- Development baseline defaults master/global mode ON to force an AIDL session-0 test.
- Root-backed SHM is used only when enforcing SELinux blocks direct mmap.
- No broad `untrusted_app shell_data_file` rule is required.
- Dolby/Oplus/MTK audio services remain untouched.
