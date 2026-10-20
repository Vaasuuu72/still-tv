# TV baseline

Captured UTC: 20261018T150029Z
Device: `192.168.1.8:5555`

Read-only collection. No package, setting or HOME changes were made.

## Device identity

| Property | Observed value |
|---|---|
| `ro.product.manufacturer` | SPPL |
| `ro.product.brand` | THOMSON_KODAK |
| `ro.product.model` | PATH_UHDXANDROID |
| `ro.product.device` | LEDTVUHD |
| `ro.build.version.release` | 9 |
| `ro.build.version.sdk` | 28 |
| `ro.build.display.id` | PI.20210922 release-keys |
| `ro.build.fingerprint` | THOMSON_KODAK/LEDTVUHD/LEDTVUHD:9/PI/20210922:user/release-keys |
| `ro.product.cpu.abilist` | armeabi-v7a,armeabi |
| `ro.product.cpu.abi` | armeabi-v7a |
| `ro.hardware` | amlogic |
| `ro.config.low_ram` |  |

Raw evidence: `reports/raw/192.168.1.8_5555-20261018T150029Z`. Logs can contain personal app/session information; kept out of Git.

Original resolved HOME: `com.google.android.tvlauncher/.MainActivity`.

## Display, memory and storage

### display
```text
Physical size: 1920x1080
Physical density: 320
```

### memory
```text
MemTotal:        2055092 kB
MemFree:          208860 kB
MemAvailable:    1072472 kB
Buffers:           24048 kB
Cached:           977200 kB
SwapCached:            0 kB
Active:           870000 kB
Inactive:         529644 kB
Active(anon):     358664 kB
Inactive(anon):    43352 kB
Active(file):     511336 kB
Inactive(file):   486292 kB
Unevictable:        2376 kB
Mlocked:            2376 kB
HighTotal:       1310720 kB
HighFree:          86996 kB
LowTotal:         744372 kB
LowFree:          121864 kB
SwapTotal:        262140 kB
SwapFree:         262120 kB
Dirty:               164 kB
Writeback:             0 kB
AnonPages:        400632 kB
Mapped:           667788 kB
Shmem:              1740 kB
Slab:              78012 kB
SReclaimable:      28228 kB
SUnreclaim:        49784 kB
KernelStack:       10656 kB
PageTables:        25492 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:     1289684 kB
Committed_AS:   21611700 kB
VmallocTotal:     245760 kB
VmallocUsed:           0 kB
VmallocChunk:          0 kB
CmaTotal:         413696 kB
CmaFree:           41824 kB
VmapStack:          5360 kB
[exit 1] cat: /proc/pressure/memory: No such file or directory
```

### storage
```text
Filesystem      1K-blocks    Used Available Use% Mounted on
/dev/block/data   4472016 3129344   1293520  71% /data
/data/media       4472016 3129344   1293520  71% /storage/emulated
```

### animations
```text
1.0
1.0
null
```

## Software and performance evidence

Full package inventories, component/permission details, services, processes, sampled CPU, memory, input services, preferred activities and recent warnings/crashes are in the raw evidence directory.

Unavailable commands and permission denials remain in the evidence rather than being treated as empty results.

Startup, grid latency, focus correctness, streaming/DRM and causal lag analysis require subsequent interactive tests. CPU is a sample; free RAM alone does not establish a performance problem. Rendering counters are historical, not a controlled benchmark.

## Next action

Review identity and package evidence. Validate the original HOME and Settings fallback before installing or changing defaults. Classify packages only after inspecting purpose and dependencies.
