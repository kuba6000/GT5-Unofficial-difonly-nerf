# Integrated Fluid Network Architecture

This document is the consolidated IFN redesign target. `Discovery.md` is the raw Q&A log; this file is the working architecture.

## Purpose

IFN is a gameplay system for thermodynamic fluid engineering. It replaces simple "put fluid into machine, get power/process" mechanics with a network where pressure, temperature, phase, heat loss, pressure drop, pumps, radiators, heat pumps and turbines create optimization space.

The system should be simple enough to use without doing math, but reward players who understand the thermodynamics and build better layouts.

Core constraints:

- Deterministic and low CPU cost.
- Prefer analytical/direct calculations.
- No local per-pipe flow simulation inside a connected network.
- No unbounded iteration.
- Real physics should guide relationships, but absolute numbers are calibrated for GT gameplay.

## System Model

### Network

An IFN network is a lumped thermodynamic volume. All connected infrastructure shares one solved state.

Network members include:

- IFN pipes.
- IFN hatches.
- Hydrophores/accumulators.
- Other infrastructure members that explicitly join the network.

Machines are not network members. A machine operates on one or more connected IFN networks through hatches/ports.

### Machine

A machine is a process actor. It reads solved network state, takes input from input hatches, runs its process, and outputs through output hatches.

Machines may have more than two IFN ports. Current heat pump gameplay already implies four-port machines. Ports are named and directional.

Rules:

- A hatch/port is either input or output.
- A single hatch does not reverse direction.
- Machine modes may choose which ports are active.
- Disabled machines do not transfer anything.

### Hatch

Hatches are part of the IFN network topology, but do not impose their own pressure or temperature safety limits.

Hatches should be colorable with spray cans so players can label multi-port machines, for example `A -> C` and `B -> D`.

### Hydrophore

A hydrophore is passive compliance.

Rules:

- It contributes effective network capacity from `0 L` to about `10000 L`, depending on configuration/pressure.
- It updates immediately when configured.
- It does not add or remove energy.
- It does not perform useful work.
- It preserves `substanceAmount` and internal energy while changing effective volume; the solver recomputes pressure, temperature and phase.

## Canonical State

The network stores only conserved canonical state as truth:

- `fluidId` or empty.
- `substanceAmount` as fixed-point integer.
- `internalEnergyEU` as fixed-point integer EU.
- Topology-derived `networkVolumeL` as fixed-point volume.

Derived state is solved from canonical state:

- Pressure.
- Temperature.
- Phase composition.
- Quality/fractions.
- Density/specific volume.
- Liquid/vapor/current volumes.
- Safety diagnostics.

Derived state may be cached within runtime, but is invalidated after any canonical/topology change. It is not authoritative persisted state.

### Units

Player-facing units:

- Substance: `refL`.
- Pressure: `bar`.
- Temperature: `K`.
- Volume: `L`.
- Flow: `L/t`.
- Energy/power: `EU`, `EU/t`.

Backend energy is also EU. There is no fixed Joule-to-EU conversion.

`refL` is not physical volume at a reference pressure/temperature. It is a mole-like IFN amount-of-substance unit. It is the marker of extractable matter.

Boundary conversion:

- GT input: `1 L -> 1 refL`.
- GT output: `1 refL -> 1 L`.

Heating, cooling, compression, expansion and phase change never create or destroy `refL`.

## Solver Boundary

The core must separate pure solving from side effects.

### State Solver

`StateSolver` is pure and deterministic.

Input:

- Canonical state.
- Fluid property record.
- Network volume.
- Ambient/process context where needed.

Output:

- Solved pressure.
- Solved temperature.
- Phase composition.
- Quality/fractions.
- Density/current volumes.
- Flowable fraction.
- Diagnostics.

The solver should be designed for liquid, vapor/gas, liquid+vapor two-phase and supercritical states first. `Solid` and richer phase composition should be supported by architecture, but can be implemented later if needed.

### Safety Rules

Safety evaluation is separate from the solver.

It checks:

- Weakest-link max temperature.
- Weakest-link max pressure differential relative to ambient pressure.
- Over-limit warning state.
- Immediate rupture threshold.
- Rupture risk timer inputs.

### Runtime Tick

Runtime code applies side effects:

- Passive heat exchange.
- Over-limit timers.
- Rupture/failure events.
- Voiding contents.
- Pending/frozen state transitions.
- Persistence updates.

Read-only queries must not rupture networks.

### Machine Process Layer

Machine processes:

- Read solved states.
- Compute available input and output acceptance.
- Apply machine limits.
- Create a transfer plan.
- Commit canonical state changes atomically.

Processes must not mutate networks during "can I run?" checks.

## Phase Model

Required phase components:

- `Liquid`.
- `Gas`/`Vapor`.
- `Supercritical`.
- `Solid`.

The model should allow phase composition if the solver can represent it deterministically. Examples:

- `Liquid + Vapor`.
- `Solid + Liquid`.

Initial implementation may use a smaller stable subset.

`Quality` means vapor substance fraction:

```text
vapor refL / total refL
```

It is not vapor volume fraction.

### Solid

`Solid` is a problematic phase, not a separate topology freeze state.

Rules:

- Fully solid contents block machine extraction/flow.
- Multi-phase contents with some flowable fraction allow extraction only from the flowable fraction.
- Solid does not create local pipe resistance because there is no local flow simulation inside a lumped network.
- Solid does not directly damage pipes initially.
- Solid contents still participate in passive heat exchange.
- If phase leaves `Solid`, flow can resume automatically.
- If solid contents are broken/ruptured, contents are voided.
- No special heat-pump rescue mode without flow is required.

## Topology Changes

### Merge

Compatible merge:

- Add `substanceAmount`.
- Add `internalEnergyEU`.
- Rebuild topology/volume.
- Solve new state.
- Apply safety rules after merge.

Empty + non-empty is compatible. Empty has no active fluid.

Non-empty + non-empty with different fluids is incompatible.

Conflict policy:

- If a simple pipe connection can be prevented, disconnect/refuse the pipe connection and keep both networks normal.
- If the conflict cannot be safely auto-prevented, freeze both affected networks until the offending connection/block is removed.

### Split

When a lumped network splits:

- Distribute `substanceAmount` proportionally to resulting volumes.
- Distribute internal energy proportionally to resulting volumes.
- Try to preserve equal/consistent pressure/state.
- Rounding must be deterministic.
- Rounding loss is acceptable.
- Split must never create extra substance or energy.

## Pending And Frozen

### Pending

Pending means topology is incomplete due to loading/chunk availability.

Pending networks:

- Preserve state.
- Do not input.
- Do not output.
- Do not allow machines to operate on them.
- Do not apply passive heat exchange while topology is incomplete.

When pending resolves, rebuild topology aggregates, invalidate solved state and resume normal operation. If the full rebuild shows the world actually changed, treat it as a normal topology change.

### Frozen

Frozen means invalid/conflicting topology.

Frozen networks:

- Preserve state.
- Do not input.
- Do not output.
- Do not allow machines to operate on them.
- Still apply passive heat exchange to prevent hot/cold freeze exploits.
- Show reason in UI/WAILA.
- Show an offending position if cheaply available.

## Passive Heat Exchange

Passive heat exchange changes internal energy, not `substanceAmount`.

It moves solved temperature toward ambient based on `dT` and does not overshoot ambient.

Initial aggregation:

- Pipe count/material/type contributions.
- Hatch contributions.
- Weighted/effective coefficients.

No local per-pipe temperature is required.

Ambient temperature and pressure are dimension-based initially; biome-specific ambient can be added later.

## Machines

### Batch Lifecycle

Default lifecycle:

1. Machine takes input into explicit active process state.
2. Active process state evolves under machine physics.
3. Finished output state is produced.
4. Finished output waits for output transfer.
5. Finished output blocks starting a new process.

Finished output is stable by default and is not further processed by the machine. This prevents target modes from overshooting while output is blocked.

Active and finished batch state must be persisted. Machines never catch up unloaded/offline time.

### Pumps

Pumps are ordinary machines, not topology elements.

Pump modes:

- Target output pressure.
- Target pressure differential.
- Target flow.

A pump pushes above input pressure by consuming energy. If its requested target cannot be reached, it performs the maximum achievable effect under current machine and network constraints. It does not need to display abstract "cannot reach setpoint" reasons; concrete current values are enough.

Pump profiles:

- Normal gas pump.
- Vacuum gas pump.
- Liquid pump.

Gas/liquid cross-use may be allowed with penalties.

### Radiator

Radiator is a machine with input and output.

It does not consume EU. It operates using pressure drop/flow conditions.

It takes a batch, exchanges heat with ambient through modular structure, then outputs the result.

Modes:

- Target temperature: hold active batch until target is reached, then finish.
- Fixed time: process for configured duration.

The modular structure should preserve the current gameplay intent:

- Conduction modules.
- Heat-exchange modules.
- Effective conductance from structure.
- Radiative contribution can depend on temperature.

### Heat Pump

Heat pump is a configurable process machine. It should keep the player-facing idea of setpoints and diagnostics, but not the current implementation shape.

Valid control modes can include:

- Target temperature.
- Target COP.
- Target energy.

Heat pump does not inherently raise pressure like a pump. For process machines such as heat pumps and radiators, output pressure should not exceed input pressure minus machine pressure loss unless a pump provides pressure elsewhere.

### Turbine

Turbine is one generalized machine family for extracting energy from pressure drop/expansion.

Rules:

- Flow is computed from `dP`.
- Turbine does not force target flow like a pump.
- Energy output derives from pressure drop/expansion and efficiency.
- Outlet temperature is solver result from expansion/energy balance, not an arbitrary temperature penalty.

Turbine blades:

- Are item components.
- Have durability.
- Have optimal operating envelopes.
- Can lose efficiency/throughput or take durability damage outside envelope.
- Take heavy durability penalty when condensation/two-phase formation occurs during expansion.

Blade failure:

- Blade breaks/disappears.
- Enabled turbine without blades acts as a low-resistance bypass with small pressure loss.
- Bypass is an active machine transfer, not topology merge.
- Disabled turbine blocks flow regardless of blade state.

## Automation

Machine enable/disable is redstone-based.

Initial detector covers:

- Pressure detector.
- Temperature detector.

Detector cover features:

- GT-style cover, not standalone block.
- Binary mode `0/15`.
- Linear mode mapping `[min, max] -> 0..15`.
- Binary is default.
- Reads whole network solved state.
- Pressure source mode: absolute pressure or differential vs ambient pressure.
- Temperature source mode: absolute temperature or delta vs ambient temperature.

## WAILA / UI

UI text should be English.

Recommended WAILA for IFN members:

- Status: `Normal`, `Pending`, `Frozen`.
- Fluid/material or `Empty`.
- `Substance: X refL`.
- `Network Volume: Y L`.
- Pressure in `bar`.
- Temperature in `K`.
- Phase composition.
- `Quality` for vapor fraction where meaningful.
- Relevant phase volumes, for example `Liquid Volume`, `Vapor Volume`.
- Passive heat exchange in `EU/t` and `dT`.
- Network members and pipe count.
- Weakest-link max temperature.
- Weakest-link max pressure differential.
- Simple over-limit warning.

Do not show raw fixed-point internals. Do not use the old `Std Amount` label. Specific enthalpy belongs in debug/dev tooling, not normal WAILA.

