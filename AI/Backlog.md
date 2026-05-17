# IFN Backlog

## Network Debug Overlay

What: Visualize network ID, pending state, capacity, pressure, temperature, and member count in-game.

Why: IFN behavior is hard to debug from logs alone.

Trigger: Start after topology and persistence invariants are stable.

Risk if ignored: Debugging large or chunk-crossing networks stays slow.

## Per-Fluid Calibration Tables

What: Expand `IFNFluidThermalRegistry` with more accurate or gameplay-tuned properties per fluid family.

Why: Thermodynamic behavior depends heavily on fluid property quality.

Trigger: Start when the core model is stable and machines need better balancing.

Risk if ignored: The system may be mechanically correct but feel arbitrary.

## Chunk Boundary Stress Tests

What: Add tests or harnesses for partial load/unload behavior and pending networks.

Why: Minecraft saves and chunks are the likely source of rare IFN bugs.

Trigger: Start before release or before changing persistence.

Risk if ignored: Networks may duplicate, void, or reject fluid after chunk reloads.

## Network Visualization Documentation

What: Add diagrams for merge, split, pending, and process planning flows.

Why: The system is concept-heavy and future contributors need fast onboarding.

Trigger: Start after first code redesign pass lands.

Risk if ignored: Future changes may reintroduce accidental coupling.

## Benchmark Harness

What: Add simple performance checks for large network rebuilds and process loops.

Why: Low computational complexity is a hard requirement.

Trigger: Start before optimizing or before changing flood-fill and planner search behavior.

Risk if ignored: Optimizations may be guided by guesses instead of measurements.
