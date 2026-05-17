# IFN Thermodynamic Network Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fragile current IFN implementation with a deterministic, conserved, phase-aware thermodynamic network architecture.

**Architecture:** Canonical state is separated from derived solved state. Topology, solver, safety runtime and machine processes are split into focused modules. Machines operate through named directional ports and commit canonical state changes through planners.

**Tech Stack:** Java, Minecraft/GT5 metatileentities, existing IFN package under `gregtech.api.metatileentity.implementations.integratedfluid`, JUnit tests.

---

## Reference Documents

- `AI/Current/Architecture.md`
- `AI/Current/Rules.md`
- `AI/Current/Glossary.md`
- `AI/Current/Cheatsheet.md`
- `AI/Current/TODO.md`

## Current High-Risk Areas

- `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/IntegratedFluidNetwork.java`
- `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/NetworkManager.java`
- `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/IFNPressureModel.java`
- `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/IFNStateTransferPlanner.java`
- `src/main/java/gregtech/common/tileentities/machines/multi/MTEHeatPump.java`
- `src/main/java/gregtech/common/tileentities/machines/multi/MTERadiator.java`

## Verification Commands

Use focused tests while refactoring:

```powershell
.\gradlew.bat test --tests "*IFN*"
.\gradlew.bat test --tests "*IntegratedFluid*"
.\gradlew.bat test --tests "*Radiator*"
.\gradlew.bat test --tests "*HeatPump*"
```

Before a merge-quality checkpoint:

```powershell
.\gradlew.bat test
```

Full-scale game verification checkpoints:

- Use Horizon-QA as a separate integration/GameTest layer, not as part of the fast TDD loop.
- Run Horizon-QA after completing or materially changing topology rebuilds, conflict handling, operational safety failure behavior, passive heat exchange, and machine process migration.
- Keep Horizon-QA tests focused on behavior that requires a real Minecraft/GTNH runtime: placed pipes/hatches, block removal, network rebuilds across chunks, machine formation/operation, WAILA-observable state, and real pipe failure effects.
- Do not block every small commit on Horizon-QA. Treat it as a batch checkpoint before merging a milestone or before declaring a system slice game-ready.
- Horizon-QA initial IFN scenarios should cover:
  - Pipe plus hatch placement forms a readable IFN network.
  - Pipe-pipe incompatible merge disconnects pipes instead of freezing both networks.
  - Hatch or non-pipe incompatible merge freezes both networks.
  - Frozen network blocks input/output/machines but still loses heat.
  - Pending/incomplete network blocks input/output/machines and does not apply passive heat loss.
  - Over-pressure or over-temperature selects a weakest pipe, voids network contents, and removes/breaks that pipe in-world.
  - Machine-mediated transfer respects pressure/temperature limits and never duplicates `refL`.

## Task 1: Lock Current Conservation Expectations

**Files:**

- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/IFNConservationTest.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/IFNTopologyDistributionTest.java`

- [x] Add tests proving `1 L` GT input becomes `1 refL` and `1 refL` output becomes `1 L`.
- [x] Add tests proving heating and gas expansion do not change extractable `refL`.
- [x] Add tests proving split never creates extra substance or energy.
- [x] Add tests proving merge sums substance and energy.
- [x] Run `.\gradlew.bat test --tests "*IFNConservationTest*" --tests "*IFNTopologyDistributionTest*"`.

Expected result before implementation: at least one test fails against current behavior or terminology. Expected result after implementation: all new conservation tests pass.

## Task 2: Introduce Canonical Value Types

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/SubstanceAmount.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/EnergyAmount.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/VolumeAmount.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/Pressure.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/Temperature.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/amount/IFNAmountTypesTest.java`

- [x] Add fixed-point wrappers with explicit factory methods from player units.
- [x] Add deterministic rounding helpers for split/transfer.
- [x] Add tests for no overflow on expected network sizes.
- [x] Add tests for lossy split rounding that never creates extra units.
- [x] Run `.\gradlew.bat test --tests "*IFNAmountTypesTest*"`.

## Task 3: Extract Canonical Network State

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/state/IFNCanonicalState.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/state/IFNSolvedState.java`
- Modify: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/IntegratedFluidNetwork.java`
- Modify: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/IntegratedFluidNetworkSavedData.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/IFNCanonicalStatePersistenceTest.java`

- [x] Move persisted truth into `IFNCanonicalState`.
- [x] Remove authoritative persisted pressure/temperature.
- [x] Keep migration from legacy NBT fields.
- [x] Add solved-state cache invalidated by canonical/topology changes.
- [x] Run persistence tests.

## Task 4: Build Closed Fluid Registry And Solver Boundary

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/fluid/IFNFluidDefinition.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/fluid/IFNFluidRegistry.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/solver/IFNStateSolver.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/solver/IFNPhaseComposition.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/solver/IFNStateSolverTest.java`

- [x] Register water/steam, hydrogen, helium, oxygen and nitrogen as hardcoded supported fluids.
- [x] Reject unsupported fluids silently at IFN input.
- [x] Implement stable liquid/gas/two-phase/supercritical solved state subset.
- [x] Expose flowable fraction and phase volumes.
- [x] Keep solid/multi-phase composition API-compatible even if not fully solved yet.

## Task 5: Separate Topology From State

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNTopologySnapshot.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNTopologyRebuilder.java`
- Modify: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/NetworkManager.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNTopologyRebuilderTest.java`

- [x] Compute volume/capacity from infrastructure members.
- [x] Include pipe, hatch and hydrophore contributions.
- [x] Implement pending when topology is incomplete.
- [x] Implement normal rebuild after pending resolves.
- [x] Treat changed full topology as normal split/merge.

## Task 6: Implement Merge, Split And Conflict Policies

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNStateDistributor.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNMergePolicy.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/topology/IFNMergeSplitPolicyTest.java`

- [x] Implement compatible merge by summing canonical state.
- [x] Implement empty + non-empty merge.
- [x] Implement proportional split by resulting volume.
- [x] Implement incompatible pipe connection prevention.
- [x] Implement frozen fallback for unpreventable incompatible connections.

## Task 7: Extract Safety Runtime

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/safety/IFNSafetyLimits.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/safety/IFNSafetyEvaluator.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/safety/IFNRuptureRuntime.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/safety/IFNSafetyEvaluatorTest.java`

- [x] Use weakest-link max temperature and max pressure differential.
- [x] Implement over-limit warning state.
- [x] Keep timers/probabilities out of normal UI.
- [x] Inject deterministic RNG for rupture tests.
- [x] Ensure read-only solved-state queries cannot rupture networks.

## Task 8: Implement Passive Heat Exchange Runtime

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/thermal/IFNAmbientContext.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/thermal/IFNHeatExchangeRuntime.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/thermal/IFNHeatExchangeRuntimeTest.java`

- [x] Aggregate heat exchange from pipe/material/type and hatches.
- [x] Use dimension ambient initially.
- [x] Move temperature toward ambient without overshoot.
- [x] Apply heat exchange to frozen networks.
- [x] Do not apply heat exchange to pending networks.

## Task 9: Add Machine Process Framework

**Files:**

- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/machine/IFNPort.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/machine/IFNMachineProcess.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/machine/IFNBatchState.java`
- Create: `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/machine/IFNTransferPlan.java`
- Test: `src/test/java/gregtech/api/metatileentity/implementations/integratedfluid/machine/IFNMachineProcessTest.java`

- [x] Model named directional ports.
- [x] Model active batch and finished output.
- [x] Persist machine-local process state.
- [x] Block next process while finished output remains.
- [x] Ensure disabled machines block all machine-mediated transfer.

## Task 10: Migrate Machines

**Files:**

- Modify: `src/main/java/gregtech/common/tileentities/machines/multi/MTERadiator.java`
- Modify: `src/main/java/gregtech/common/tileentities/machines/multi/MTEHeatPump.java`
- Create/modify pump machine files according to existing registration patterns.
- Create/modify turbine machine files according to existing registration patterns.
- Test: existing and new `*Radiator*`, `*HeatPump*`, `*Pump*`, `*Turbine*` tests.

- [x] Migrate radiator to no-EU pressure-drop batch process.
- [x] Migrate heat pump to planner-first controlled thermal process.
- [x] Implement pump modes: target output pressure, target pressure differential, target flow.
- [x] Implement turbine as dP-driven energy extraction.
- [x] Implement turbine blade item durability and no-blade bypass.

## Task 11: UI, WAILA And Covers

**Files:**

- Modify IFN pipe/hatch WAILA classes in `src/main/java/gregtech/api/metatileentity/implementations/integratedfluid/`.
- Create pressure detector cover class following existing GT cover patterns.
- Create temperature detector cover class following existing GT cover patterns.
- Test cover behavior with focused unit tests where existing cover infrastructure allows it.

- [x] Replace `Std Amount` with `Substance: X refL`.
- [x] Add phase-aware volume display.
- [x] Add weakest-link network limits to WAILA.
- [x] Add pressure and temperature detector covers.
- [x] Add binary and linear output modes.
- [x] Add absolute/delta source modes.
- [x] Keep UI text English.

## Final Acceptance Criteria

- IFN canonical state conserves `refL` and EU except explicit loss/void/work/heat exchange.
- No thermodynamic state change can duplicate extractable GT fluid.
- Pressure and temperature are derived, not authoritative mutable storage.
- Pending/frozen states behave differently and are tested.
- Merge/split behavior is deterministic and tested.
- Machines use process plans and do not mutate networks in availability checks.
- WAILA uses `refL`, phase-aware volumes and weakest-link limits.
- Full targeted IFN test suite passes.
