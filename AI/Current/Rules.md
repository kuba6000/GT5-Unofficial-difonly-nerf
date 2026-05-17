# IFN Engineering Rules

These are hard design rules for the IFN redesign.

## Conservation

- `substanceAmount` is conserved except for explicit input, output, void, split rounding loss, or rupture/failure.
- `internalEnergyEU` is conserved except for explicit machine work, heat exchange, output/input boundary behavior, void, split rounding loss, or rupture/failure.
- `refL` is the player-facing conserved amount-of-substance unit.
- `1 L` GT fluid entering IFN becomes `1 refL`.
- `1 refL` leaving IFN becomes `1 L` GT fluid.
- Thermodynamic expansion/compression/heating/cooling never changes `refL`.

## State

- Canonical state stores fluid id, `substanceAmount`, `internalEnergyEU`, and topology-derived volume.
- Pressure, temperature, phase, density, volumes and quality are derived.
- Derived state may be cached, but cannot be persisted as authoritative truth.
- Empty means exactly zero `substanceAmount`; trace nonzero amounts are preserved.
- Empty UI may display ambient values, but ambient is not canonical state.

## Units

- Substance: `refL`.
- Pressure: `bar`.
- Temperature: `K`.
- Volume: `L`.
- Flow: `L/t`.
- Energy: `EU`.
- Power: `EU/t`.
- Runtime energy scale is EU; there is no fixed J-to-EU relation.

## Topology

- One connected network has one lumped solved thermodynamic state.
- There is no local flow inside a network.
- Machines are not part of networks.
- Hatches are network members but do not impose pressure/temperature limits.
- Network volume comes from infrastructure members, including pipes, hatches and hydrophores.
- Split distributes substance and energy proportionally by resulting volumes and must be deterministic/lossy rather than duplicating.
- Merge sums canonical state and solves the new state.

## Compatibility

- Initial scope supports one active fluid/material per network.
- Empty networks are compatible with any supported IFN fluid.
- Non-empty networks with different fluids are incompatible.
- Simple pipe conflicts should be prevented/disconnected.
- Non-preventable incompatible connections freeze both affected networks.
- Unsupported GT fluids are ignored by IFN input.

## Runtime States

- Pending is incomplete loading/topology availability.
- Frozen is invalid/conflicting topology.
- Pending and frozen both block input, output and machine processing.
- Frozen still applies passive heat exchange.
- Pending does not apply passive heat exchange while topology is incomplete.
- No machine or network catches up unloaded/offline time.

## Safety

- Pipe safety uses max pressure differential relative to ambient, not only absolute pressure.
- Network safety uses weakest-link limits.
- Over-limit under rupture threshold shows a simple warning.
- Do not expose failure timers or probabilities to players.
- Immediate rupture occurs at severe over-limit threshold.
- Rupture voids contents.
- Safety valves/relief devices are future extension points.

## Heat Exchange

- Passive heat exchange changes energy, not `substanceAmount`.
- It moves solved temperature toward ambient and does not overshoot.
- It is aggregated from infrastructure materials/types and hatches.
- Initial ambient is per dimension.

## Machine Processes

- Machines declare their own control modes.
- There is no single universal machine UX.
- Machines can be tick-continuous or batch-based.
- Batch input should be taken at process start.
- Active batch is explicit machine-local state.
- Finished output is stable by default and waits for output transfer.
- Finished output blocks the next process.
- Active and finished process state must be persisted.
- Disabled machines block machine-mediated transfer.

## Phase

- Initial solver should support stable liquid, vapor/gas, liquid+vapor two-phase and supercritical states.
- Architecture should not block richer phase composition.
- `Quality` means vapor `refL / total refL`.
- `Solid` is future-compatible and represents problematic non-flowable material.
- Fully solid contents block machine extraction.
- Multi-phase solid contents limit extraction to flowable fraction.

## UI

- IFN UI text is English.
- WAILA should show network-level solved state, not local pipe state.
- WAILA should show `Substance: X refL`.
- WAILA should not show raw fixed-point values or `Std Amount`.
- Item tooltips show local item stats.
- Built network WAILA shows whole-network weakest-link limits.

