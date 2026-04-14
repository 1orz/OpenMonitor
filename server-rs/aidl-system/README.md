# Hidden AOSP AIDL Sources

Files in this tree are **manually extracted from AOSP** (`frameworks/base/**`)
so that the Rust server can speak system_server's binder protocols without
going through reflection or a shell. They are compiled into client-side
stubs by `rsbinder-aidl` in `build.rs`.

## Source map

| Relative path                                       | AOSP source                                                     | Purpose in server |
|-----------------------------------------------------|-----------------------------------------------------------------|-------------------|
| android/app/IActivityTaskManager.aidl               | frameworks/base/core/java/android/app/IActivityTaskManager.aidl | Register focus listener |
| android/app/ITaskStackListener.aidl                 | frameworks/base/core/java/android/app/ITaskStackListener.aidl   | Receive focus change events |
| android/app/RunningTaskInfo.aidl (stub Parcelable)  | frameworks/base/core/java/android/app/ActivityManager.java      | topActivity component name |
| android/os/IBatteryPropertiesRegistrar.aidl         | frameworks/base/core/java/android/os/IBatteryPropertiesRegistrar.aidl | getProperty(BATTERY_PROPERTY_CURRENT_NOW) |
| android/os/BatteryProperty.aidl                     | frameworks/base/core/java/android/os/BatteryProperty.java       | getProperty out-param |
| android/hardware/display/IDisplayManager.aidl       | frameworks/base/core/java/android/hardware/display/IDisplayManager.aidl | Screen on/off events |
| android/os/IPowerManager.aidl                       | frameworks/base/core/java/android/os/IPowerManager.aidl         | isInteractive() fallback |
| android/content/ComponentName.aidl (Parcelable stub)| frameworks/base/core/java/android/content/ComponentName.java    | topActivity field |
| android/app/IActivityManager.aidl                   | frameworks/base/core/java/android/app/IActivityManager.aidl     | getContentProviderExternal (libsu path) |
| android/content/IContentProvider.aidl               | frameworks/base/core/java/android/content/IContentProvider.aidl | provider.call("setBinder", ...) |

## Extraction rules

1. Each `.aidl` must be fed through `aidl --lang=rust` (via rsbinder-aidl) and
   produce a compiling Rust client stub.
2. Parcelable deps that have a full AIDL definition (rare in framework) are
   copied verbatim. The more common case — Parcelable implemented in Java — is
   handled by declaring `parcelable Foo;` as a **stub** here and manually
   matching the `writeToParcel` field order in Rust (`src/parcel/` when needed).
3. Only the methods we actually call need full type-correctness — unused
   methods can be declared `oneway` with placeholder parameters to keep the
   stub compiling without pulling in unrelated dependencies.

## Compatibility

Different Android versions rearrange these AIDLs (fields added to
`RunningTaskInfo`, transaction codes shift). When a method has drifted the
generated Rust client will serialize the wrong transaction ID and the system
side will reply with `EX_TRANSACTION_FAILED`. We cope by:

- Pinning transaction codes with `= N` wherever the AOSP AIDL does (most do).
- For auto-numbered methods, verifying against the AOSP source of the target
  API level at runtime (Phase 4 will add an `android_api_level` gate).

TODO(Phase 4): populate this tree. For now the `build.rs` tolerates an empty
directory so the minimal "hello world" crate still compiles.
