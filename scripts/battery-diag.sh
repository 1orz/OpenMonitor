#!/system/bin/sh
# OpenMonitor Battery Sysfs Diagnostic Script
# Usage: adb shell sh /data/local/tmp/battery-diag.sh
#   or:  adb shell su -c sh /data/local/tmp/battery-diag.sh  (root)
#
# Run under different permissions and paste the output back.

echo "========================================"
echo "  OpenMonitor Battery Diagnostics"
echo "========================================"
echo ""

# ---- Section 1: Environment ----
echo "==== 1. ENVIRONMENT ===="
echo "Date:     $(date)"
echo "User:     $(id)"
echo "SELinux:  $(getenforce 2>/dev/null || echo 'N/A')"
echo "Context:  $(cat /proc/self/attr/current 2>/dev/null || echo 'N/A')"
echo ""

# Device info
echo "Device:   $(getprop ro.product.model)"
echo "Brand:    $(getprop ro.product.brand)"
echo "Android:  $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"
echo "MIUI:     $(getprop ro.miui.ui.version.name 2>/dev/null)"
echo "HyperOS:  $(getprop ro.mi.os.version.name 2>/dev/null)"
echo "OPlus:    $(getprop ro.oplus.version.os 2>/dev/null)"
echo "Kernel:   $(uname -r)"
echo ""

# ---- Section 2: Power Supply Directories ----
echo "==== 2. POWER SUPPLY LIST ===="
ls -la /sys/class/power_supply/ 2>&1
echo ""

# ---- Section 3: Battery uevent (comprehensive) ----
echo "==== 3. BATTERY UEVENT ===="
cat /sys/class/power_supply/battery/uevent 2>&1
echo ""

# ---- Section 4: All battery sysfs files - permissions & values ----
echo "==== 4. BATTERY SYSFS FILES (all) ===="
BAT_DIR="/sys/class/power_supply/battery"
for path in "$BAT_DIR"/*; do
    f=$(basename "$path")
    # skip directories (device, power, subsystem are symlinks to dirs)
    [ -d "$path" ] && { printf "  %-28s [dir/symlink]\n" "$f"; continue; }
    perm=$(ls -la "$path" 2>/dev/null | awk '{print $1, $3, $4}')
    val=$(cat "$path" 2>&1)
    printf "  %-28s %s = %s\n" "$f" "$perm" "$val"
done
echo ""

# Check other power_supply entries (USB, charger, etc.)
echo "==== 5. OTHER POWER SUPPLY (all files) ===="
for dir in /sys/class/power_supply/*/; do
    name=$(basename "$dir")
    [ "$name" = "battery" ] && continue
    echo "--- $name ---"
    for path in "$dir"*; do
        f=$(basename "$path")
        [ -d "$path" ] && continue
        val=$(cat "$path" 2>&1)
        printf "  %-28s = %s\n" "$f" "$val"
    done
    echo ""
done

# ---- Section 6: current_now sampling (10 reads, 200ms apart) ----
echo "==== 6. CURRENT_NOW SAMPLING (10x, ~200ms interval) ===="
echo "  Timestamp           current_now   voltage_now   status"
i=0
while [ $i -lt 10 ]; do
    ts=$(date '+%H:%M:%S.%N' 2>/dev/null || date '+%H:%M:%S')
    cur=$(cat /sys/class/power_supply/battery/current_now 2>&1)
    vol=$(cat /sys/class/power_supply/battery/voltage_now 2>&1)
    sta=$(cat /sys/class/power_supply/battery/status 2>&1)
    printf "  %s  %12s  %12s  %s\n" "$ts" "$cur" "$vol" "$sta"
    i=$((i + 1))
    # busybox sleep supports fractions; toybox may not
    sleep 0.2 2>/dev/null || usleep 200000 2>/dev/null || sleep 1
done
echo ""

# ---- Section 7: Unit detection heuristic ----
echo "==== 7. UNIT ANALYSIS ===="
cur_val=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
vol_val=$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
if [ -n "$cur_val" ]; then
    abs_cur=${cur_val#-}
    echo "  current_now raw = $cur_val"
    if [ "$abs_cur" -gt 100000 ] 2>/dev/null; then
        echo "  -> Likely in uA (abs > 100000)"
    elif [ "$abs_cur" -gt 100 ] 2>/dev/null; then
        echo "  -> Likely in mA (abs 100-100000)"
    elif [ "$abs_cur" -gt 0 ] 2>/dev/null; then
        echo "  -> VERY SMALL (abs 1-100), ambiguous unit"
    else
        echo "  -> Zero or unreadable"
    fi
else
    echo "  current_now: UNREADABLE"
fi
if [ -n "$vol_val" ]; then
    echo "  voltage_now raw = $vol_val"
    if [ "$vol_val" -gt 100000 ] 2>/dev/null; then
        echo "  -> Likely in uV"
    elif [ "$vol_val" -gt 100 ] 2>/dev/null; then
        echo "  -> Likely in mV"
    else
        echo "  -> VERY SMALL, ambiguous unit"
    fi
else
    echo "  voltage_now: UNREADABLE"
fi
echo ""

# ---- Section 8: OPlus-specific paths ----
echo "==== 8. OPLUS GAUGE PATHS ===="
for p in \
    "/proc/oplus-votable/GAUGE_UPDATE/force_val" \
    "/proc/oplus-votable/GAUGE_UPDATE/force_active" \
    "/proc/oplus_chg/gauge_update" \
    "/proc/batt_param_noplug"; do
    if [ -e "$p" ]; then
        val=$(cat "$p" 2>&1)
        echo "  [EXISTS] $p = $val"
    else
        echo "  [ABSENT] $p"
    fi
done
echo ""

# ---- Section 9: Thermal zones (battery-related) ----
echo "==== 9. BATTERY THERMAL ===="
for tz in /sys/class/thermal/thermal_zone*/; do
    type=$(cat "${tz}type" 2>/dev/null)
    case "$type" in
        *battery*|*batt*|*Battery*|*pm8*|*charger*|*skin*)
            temp=$(cat "${tz}temp" 2>/dev/null)
            echo "  $(basename $tz): type=$type temp=$temp"
            ;;
    esac
done
echo ""

# ---- Section 10: BatteryManager via dumpsys ----
echo "==== 10. DUMPSYS BATTERY ===="
dumpsys battery 2>&1
echo ""

echo "==== 11. DUMPSYS BATTERYSTATS (header) ===="
dumpsys batterystats 2>&1 | head -30
echo ""

# ---- Section 12: SELinux audit for battery access ----
echo "==== 12. SELINUX AUDIT (last 20 battery-related denials) ===="
dmesg 2>/dev/null | grep -i "avc.*battery\|avc.*power_supply" | tail -20
if [ $? -ne 0 ]; then
    echo "  (no denials found or dmesg not accessible)"
fi
echo ""

# ---- Section 13: /proc/stat sample (CPU reference) ----
echo "==== 13. /proc/stat (first 2 lines) ===="
head -2 /proc/stat 2>&1
echo ""

echo "========================================"
echo "  Diagnostics complete."
echo "  Please paste ALL output above."
echo "========================================"
