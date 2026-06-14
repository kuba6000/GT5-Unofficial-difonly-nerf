# Full Game Server QA

`fullGameServerQA` is the workflow gate for VFN behavior that must be proven inside a real dedicated server. It is intentionally separate from `horizonsQA`: the old Horizon-QA task remains useful for fast non-server coverage, but the final confidence gate for VFN gameplay is the headless server.

## Command

```powershell
.\gradlew.bat --offline --no-daemon fullGameServerQA
```

The task depends on `runServer`, prepares `run/server/eula.txt`, forces `online-mode=false`, and starts the functional test mod with:

- `-Dgt5.fullGameQA=true`
- `-Dgt5.test.package=gregtech.test.fullgame`
- `-Dgt5.test.reportDir=./junit-full-game-out/`

The test mod shuts the server down after the JUnit run. Results are written to:

```text
run/server/junit-full-game-out/TEST-junit-jupiter.xml
```

## Current Coverage

The current full-game gate covers:

- dedicated server and loaded overworld sanity;
- placed VFN pipe joining/creating a real server-side network;
- physical VFN injector/pipe/hatch connectivity, including required hatch front-facing and pipe side connections;
- GT++ creative fluid tank producing water;
- water transfer into a Super Tank;
- Super Tank output into a VFN injector;
- VFN input network feeding a Heat Pump;
- Heat Pump processing into an output VFN;
- output VFN draining into an output Super Tank;
- Heat Pump target COP mode;
- Heat Pump target energy mode;
- Heat Pump split-flow mode with red/blue output VFNs;
- Heat Pump heat-exchanger mode with red/blue input and output VFNs;
- VFN frozen-network transfer blocking and recovery;
- VFN injector pressure cutoff;
- connected VFN injector rejection of an unsupported fluid;
- VFN split after removing the only connecting pipe;
- VFN saved-data NBT persistence of network id, fluid, and stored amount;
- destructive over-pressure failure selecting a physical server-world pipe and voiding network fluid.

## Test Harness

Full-game tests live under:

```text
src/functionalTest/java/gregtech/test/fullgame/
```

Use `FullGameServerQaHarness` for world placement, cleanup, ticking, GT meta tile creation, hatch facing, pipe connection, VFN rebuild, and Heat Pump hatch attachment. New full-game cases should add scenario behavior, not duplicate world setup.

## Next Coverage Targets

Remaining server scenarios worth adding when changing the corresponding gameplay:

- Heat Pump target-temperature mode as a standalone mode test, beyond the end-to-end Creative Tank chain.
- VFN transfer blocking with a physically full output network and pending Heat Pump output buffer.
- Topology changes for hatch removal, network merge, and chunk boundary placement.
- Persistence reload checks for VFN state and Heat Pump output buffer state, not only NBT write coverage.
- Temperature warning/failure behavior; pressure destructive failure is already covered.
