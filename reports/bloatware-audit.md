# Package and performance audit — TV evidence pending

No TV package list has been collected. **No package is proposed for disabling or removal, and none has been modified.** Package names alone do not establish safety.

| Group | Treatment |
|---|---|
| A — Essential | Framework, System UI, Settings, input/remote, networking, audio/media, DRM, package/permission/security infrastructure: never disable. |
| B — Potentially necessary | Vendor integrations, OTA, Google/account/platform components and unknown dependencies: investigate; OTA/hardware/security services remain protected. |
| C — Optional | Verified unused entertainment apps/utilities: assess actual background/storage cost before proposing anything. |
| D — Likely unnecessary | Verified promotional/recommendation/content services with no required dependencies: candidates only after evidence and approval. |

The read-only collector saves full package/component/permission details, system/user/disabled inventories, running services, CPU/memory samples, errors, and TV input state. After connection, classify every observed package; retain uncertain classifications in B. Inspect shared UID, libraries, bound services, vendor links and essential integrations before any proposal.

## Candidate change record

No candidates yet. For each future proposal fill in:

| Package | Group / purpose | Evidence it is unnecessary | Expected measurable benefit | Risk | Dependencies checked | Exact reversal |
|---|---|---|---|---|---|---|
| Pending device evidence | — | — | — | Unknown | Not inspected | Not applicable |

Approval must name the exact package list. Preferred reversible command after approval: `adb -s SERIAL shell pm disable-user --user 0 PACKAGE`; reversal: `adb -s SERIAL shell pm enable --user 0 PACKAGE`. Confirm the package was originally enabled before using that reversal. Preserve the original disabled/enabled state in the change log.

`pm uninstall --user 0` has no authorization and requires a separate decision with verified recovery. No scripts automatically disable or uninstall any package.

## Other optimization evidence needed

- Storage pressure: actual `/data` availability; do not assume cache clearing will help.
- Memory pressure: available memory, PSS, swap/PSI if exposed and low-memory kills; low free RAM alone is not a diagnosis.
- CPU/background load: repeat samples, identify sustained work or crash loops.
- Rendering: controlled navigation frame samples and launcher PSS, not subjective claims.
- Animations: current scales and measured latency; any settings change needs recorded reversal and validation.

Next action: connect, collect the untouched baseline, validate Still TV, then present a concrete evidence-backed list for approval.
