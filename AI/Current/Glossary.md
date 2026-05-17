# IFN Glossary

## IFN

Integrated Fluid Network. A lumped thermodynamic network for supported fluids.

## Network

A connected set of IFN infrastructure members sharing one solved thermodynamic state.

## Member

An infrastructure object that belongs to a network, such as pipe, hatch or hydrophore.

## Machine

A process actor connected to one or more networks through hatches. Machines are not network members.

## Hatch

A directional IFN port and network member. Hatches are input or output. They do not impose pressure/temperature safety limits.

## Port

A named machine connection to an IFN hatch/network, for example `hotIn`, `hotOut`, `coldIn`, `coldOut`.

## `substanceAmount`

Canonical fixed-point backend amount of substance. It is conserved and is the source of `refL`.

## `refL`

Reference liter. Player-facing amount-of-substance unit, effectively mole-like. It is not current volume. It defines how much ordinary GT fluid can be recovered from IFN.

## `internalEnergyEU`

Canonical fixed-point internal energy in EU.

## Network Volume

Topology-derived fixed-point volume/capacity in liters. Pipes, hatches and hydrophores contribute to it.

## Current Volume

Volume occupied by a phase under current solved state. For gas this is effectively the full network volume.

## Phase Composition

Solved phase state of the medium. It may contain one or more components such as liquid, vapor, supercritical or solid.

## Quality

Vapor substance fraction in a two-phase state:

```text
vapor refL / total refL
```

## Flowable Fraction

Fraction of contents that machines can extract. Solid fraction is not flowable.

## Pending

Network state caused by incomplete loading/topology availability. It blocks processing and does not apply heat exchange until resolved.

## Frozen

Network state caused by invalid/conflicting topology, such as incompatible fluids through an unpreventable connection. It blocks processing but still applies passive heat exchange.

## Hydrophore

Passive compliance/capacity member. It smooths pressure by changing effective capacity and does not add/remove energy.

## Pressure Differential

Difference between network pressure and ambient pressure. Pipe safety is based on this.

## Passive Heat Exchange

Ambient heat transfer applied by network runtime from aggregated infrastructure properties.

## Active Batch

Machine-local process charge being transformed by machine physics.

## Finished Output

Machine-local completed product waiting for output transfer. It is stable by default and blocks the next process.

## Bypass

Modeled machine transfer that passes medium from input to output without productive operation. Turbine without blade uses this while enabled.

