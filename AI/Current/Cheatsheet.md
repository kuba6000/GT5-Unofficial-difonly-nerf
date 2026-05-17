# IFN Redesign Cheatsheet

## One-Liners

- Network state is lumped.
- Machines operate on networks; they are not networks.
- `refL` is true matter amount.
- GT boundary is `1 L <-> 1 refL`.
- Pressure and temperature are derived.
- Safety is weakest-link.
- Split is proportional and never duplicates.
- Merge sums canonical state.
- Pending is loading uncertainty.
- Frozen is invalid topology.
- Disabled machines block transfer.

## Canonical State

```text
fluidId
substanceAmount  -> shown as refL
internalEnergyEU
networkVolumeL   -> from topology
```

## Derived State

```text
pressure bar
temperature K
phase composition
quality/fractions
density/current volumes
flowable fraction
safety diagnostics
```

## UI Units

```text
Substance: refL
Pressure: bar
Temperature: K
Volume: L
Flow: L/t
Energy: EU
Power: EU/t
```

## Machine Families

```text
Pump      adds pressure/flow by consuming EU
Radiator  no EU, pressure drop, ambient heat exchange batch
Heat Pump controlled thermal process, not pressure-raising
Turbine   dP-driven energy extraction, blade durability
```

## WAILA Must Show

```text
Status
Fluid/material
Substance: X refL
Network Volume: Y L
Pressure
Temperature
Phase
Quality when meaningful
Phase volumes when meaningful
Heat exchange EU/t and dT
Weakest-link limits
Simple over-limit warning
```

## Never Show As Normal UI

```text
raw fixed-point scale
Std Amount
specific enthalpy
failure timer/probability
```

