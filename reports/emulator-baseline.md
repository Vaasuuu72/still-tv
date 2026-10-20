# Emulator baseline — not Kodak TV evidence

Captured UTC: 20261018T125729Z
Device: `emulator-5554`

Read-only collection. No package, setting or HOME changes were made.

## Device identity

| Property | Observed value |
|---|---|
| `ro.product.manufacturer` | Google |
| `ro.product.brand` | google |
| `ro.product.model` | sdk_google_atv64_arm64 |
| `ro.product.device` | emulator64_arm64 |
| `ro.build.version.release` | 12 |
| `ro.build.version.sdk` | 31 |
| `ro.build.display.id` | STT9.221129.002 dev-keys |
| `ro.build.fingerprint` | google/sdk_google_atv64_arm64/emulator64_arm64:12/STT9.221129.002/9351024:user/dev-keys |
| `ro.product.cpu.abilist` | arm64-v8a |
| `ro.product.cpu.abi` | arm64-v8a |
| `ro.hardware` | ranchu |
| `ro.config.low_ram` |  |

Raw evidence: `reports/raw/emulator-5554-20261018T125729Z`. Logs can contain personal app/session information; kept out of Git.

Original resolved HOME: `com.google.android.tvlauncher/.MainActivity`.

## Display, memory and storage

### display
```text
Physical size: 1920x1080
Physical density: 320
```

### memory
```text
MemTotal:        1500772 kB
MemFree:          292308 kB
MemAvailable:     321108 kB
Buffers:            5884 kB
Cached:           274352 kB
SwapCached:            0 kB
Active:           107448 kB
Inactive:         818648 kB
Active(anon):       2472 kB
Inactive(anon):   663172 kB
Active(file):     104976 kB
Inactive(file):   155476 kB
Unevictable:        9680 kB
Mlocked:            9680 kB
SwapTotal:             0 kB
SwapFree:              0 kB
Dirty:               168 kB
Writeback:             0 kB
AnonPages:        655576 kB
Mapped:           218416 kB
Shmem:             10960 kB
KReclaimable:      47432 kB
Slab:             133896 kB
SReclaimable:      47432 kB
SUnreclaim:        86464 kB
KernelStack:       23648 kB
ShadowCallStack:    5920 kB
PageTables:        54208 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:      750384 kB
Committed_AS:   45926284 kB
VmallocTotal:   262930368 kB
VmallocUsed:       41572 kB
VmallocChunk:          0 kB
Percpu:             2040 kB
AnonHugePages:         0 kB
ShmemHugePages:        0 kB
ShmemPmdMapped:        0 kB
FileHugePages:      8192 kB
FilePmdMapped:      8192 kB
CmaTotal:          16384 kB
CmaFree:               0 kB
[exit 1] cat: /proc/pressure/memory: Permission denied
```

### storage
```text
Filesystem      1K-blocks   Used Available Use% Mounted on
/dev/block/dm-4   6082144 240704   5699228   5% /storage/emulated/0/Android/obb
/dev/fuse         6082144 240704   5699228   5% /storage/emulated
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
