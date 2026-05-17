# IFN Accepted Work Tracker

This is the accepted refactor backlog derived from the Q&A. It is ordered by dependency, not by estimated difficulty.

## Phase 0: Test Harness And Safety Net

- [ ] Add focused tests for canonical conservation: input, output, merge, split, machine transfer.
- [ ] Add deterministic fixed-point rounding tests for split.
- [ ] Add no-duplication tests for gas expansion/compression and GT boundary conversion.
- [ ] Add pending/frozen behavior tests.
- [ ] Add rupture tests with injected deterministic RNG.

## Phase 1: Core State Model

- [ ] Introduce value types for `SubstanceAmount`, `EnergyAmount`, `VolumeAmount`, `Pressure`, `Temperature`.
- [ ] Replace ambiguous `stdAmount` concepts with `substanceAmount` and `refL` presentation.
- [ ] Make canonical state explicit and persisted.
- [ ] Move pressure, temperature, phase and volumes to derived solved state.
- [ ] Keep solved state cache runtime-only and invalidated by canonical/topology changes.

## Phase 2: Fluid Registry And Solver

- [ ] Add closed hardcoded IFN fluid registry.
- [ ] Register initial fluids: water/steam, hydrogen, helium, oxygen, nitrogen.
- [ ] Implement stable liquid/gas/two-phase/supercritical solver subset.
- [ ] Keep architecture open for future solid/multi-phase composition.
- [ ] Reject unsupported fluids silently at IFN input.

## Phase 3: Topology Runtime

- [ ] Separate topology membership from fluid state.
- [ ] Implement merge rules: compatible sum, empty compatibility, incompatible freeze/disconnect.
- [ ] Implement split rules: proportional distribution, deterministic lossy rounding.
- [ ] Implement pending state for incomplete chunk loading.
- [ ] Implement frozen state for invalid topology.
- [ ] Ensure pending does not heat-exchange and frozen does.

## Phase 4: Heat Exchange And Safety

- [ ] Aggregate passive heat exchange from pipe/material/type and hatches.
- [ ] Add ambient context per dimension.
- [ ] Implement weakest-link pressure differential and temperature limits.
- [ ] Implement over-limit warnings without exposing timers.
- [ ] Implement rupture as explicit runtime event.
- [ ] Void contents on rupture.

## Phase 5: Machine Process API

- [ ] Add named directional IFN ports.
- [ ] Add common process planning API.
- [ ] Add active batch and finished output lifecycle.
- [ ] Persist active and finished process state.
- [ ] Ensure disabled machines block all machine-mediated transfer.
- [ ] Ensure machines never catch up unloaded/offline time.

## Phase 6: Machines

- [ ] Redesign pumps as ordinary machines with target output pressure, target pressure differential and target flow modes.
- [ ] Redesign radiator as no-EU pressure-drop batch machine with target-temperature and fixed-time modes.
- [ ] Redesign heat pump as controlled thermal process machine without pressure-raising behavior.
- [ ] Redesign turbine as dP-driven machine with blade item durability and no-blade bypass.

## Phase 7: Automation And UI

- [ ] Add pressure detector cover.
- [ ] Add temperature detector cover.
- [ ] Support binary and linear redstone output modes.
- [ ] Add absolute and ambient-delta source modes where applicable.
- [ ] Update WAILA to network-level solved state.
- [ ] Add pipe item tooltips for max temperature and max pressure differential.
- [ ] Add hatch spray-can coloring.

## Phase 8: Documentation And Cleanup

- [ ] Keep `AI/Current/Architecture.md` in sync with implementation decisions.
- [ ] Keep `AI/Current/Rules.md` as the invariant checklist for reviews.
- [ ] Remove obsolete IFN terminology from code comments and UI.
- [ ] Delete or isolate legacy helper code once replacement tests pass.

