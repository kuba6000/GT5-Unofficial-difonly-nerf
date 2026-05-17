# IFN Discovery Notes

These notes capture live Q&A decisions and intent for the IFN/VFN redesign. Keep this file updated during design conversations so context compaction does not lose project intent.

## 2026-05-15 - Initial Design Intent

### Purpose

IFN should expand simple GT fluid mechanics into an advanced engineering system. Current gameplay often reduces turbines, generators, and similar machines to "create the right fluid and feed it into the machine." IFN should create room for process optimization and creative system design.

The target user experience:

- Basic systems should remain understandable and usable.
- Advanced players who engage with the math should receive high rewards.
- Building elegant, complex systems should be satisfying in itself.

### Rewards For Optimization

Desired rewards include:

- Higher energy efficiency.
- Higher throughput.
- Access to new or extreme operating ranges such as high pressure, high temperature, low temperature, phase transitions, or late-game process windows.
- More interesting system layouts such as heat pumps, radiators, heat exchangers, recovery loops, cascades, and pressure/temperature control.

### Costs And Failure Modes

The system should not be a free buff. It should introduce real engineering costs:

- Inefficient builds should usually work worse through losses, lower throughput, lower efficiency, or wasted heat.
- Medium failure should occur from natural consequences such as unexpected phase transitions, pressure instability, flow blockage, or thermal imbalance.
- Severe failure such as pipe or machine damage should be possible, but only for large, physically meaningful mistakes such as sudden pressure spikes or badly uncontrolled systems. It should not punish minor mistakes by destroying major player infrastructure.

Failure severity should be gradual:

1. Soft failure: poor efficiency, throttling, fluid/energy loss.
2. Medium failure: system stall, venting/voiding, forced restart, unstable operation.
3. Hard failure: broken pipes or damaged machines for major overpressure/thermal events.

### Realism Boundary

The preference is physics-first as long as it is cheap and deterministic enough for Minecraft server performance.

Preferred model style:

- Analytical formulas where possible.
- No calculus-heavy simulation as a baseline.
- Avoid unbounded iteration.
- Avoid expensive per-pipe per-tick simulation.
- Backend math can be significantly richer than the in-game UI.
- UI should expose simplified signals so players can build sensible systems without knowing the math.

If a mechanic becomes too complex or too expensive, the owner will decide case by case whether to simplify or cut it.

### Canonical State Direction

Pressure should probably not be independent mutable state. It should be derived from the conserved amount of substance, energy/temperature state, fluid properties, and network volume/capacity.

Candidate minimal canonical state:

- Fluid/material ID.
- Conserved amount of substance.
- Total energy or enthalpy-like state.
- Network volume/capacity from topology.

Likely derived values:

- Pressure.
- Temperature.
- Phase.
- Density/specific volume.
- Occupied volume.
- Pressure drop.
- Available thermal/mechanical energy.

Open design issue: decide whether the canonical energy variable should be enthalpy, internal energy, temperature plus heat capacity, or a deliberately simplified gameplay variable.

### mB, Substance Amount, And No-Duplication Rule

External Minecraft/GT fluids use `FluidStack.amount` in mB. IFN should not treat changing gas volume as a way to create more output fluid.

Important invariant:

- IFN must conserve an internal "amount of substance" or normalized amount.
- Physical occupied volume may change with pressure, temperature, and phase.
- External `FluidStack.amount` is an interface format, not necessarily the actual network volume.

Current code already has a related concept around `stdAmount` / true amount versus nominal amount. The redesign may keep this idea but should name it clearly, e.g. `substanceAmount`, `substanceQ`, or another unambiguous term.

Exploit to prevent:

- Player inserts 100 L of gas, heats it so it occupies 200 L, then extracts 200 L as ordinary GT fluid.

Conservation should apply to internal substance amount and energy, not to current physical volume.

### IFN Boundary With Ordinary GT FluidStack

Input to IFN:

- Ordinary `FluidStack.amount` enters through an input/injector.
- The port assumes a reference input state, such as reference pressure and temperature or ambient temperature.
- The port converts external mB into internal substance amount and energy.

Inside IFN:

- The network tracks thermodynamic state.
- State can change through heat, compression, expansion, machines, losses, and phase behavior.

Output to ordinary GT pipes:

- Once fluid leaves IFN into a normal GT `FluidStack`, it has only the ordinary fluid identity and amount.
- Thermodynamic state is lost at that boundary unless the receiving system is IFN-aware.
- The ordinary GT world effectively sees a reference-state fluid.

Deferred design issue:

- Decide whether losing thermodynamic state at ordinary output simply discards energy as loss, radiates it, requires a special safe output/condenser, or causes throttling/failure for extreme states.
  - Status: explicitly deferred. The boundary between IFN and ordinary GT/Minecraft fluid systems has many possible edge cases and should be designed after the internal network model is clearer.

### Current Open Question

The next Q&A topic should move back to the internal IFN model, not the IFN-to-GT boundary.

### Fluid Mixing Scope

Initial IFN scope is single-fluid per network.

Decision:

- A network has at most one active fluid/material for now.
- Mixing different fluids in one network is out of scope for the first redesign.
- The architecture should not make future mixtures impossible.

Implications:

- Current redesign can keep one fluid ID in canonical state.
- Merge behavior for different-fluid networks needs an explicit safe policy, such as reject merge state, keep networks separate if topology allows, or choose a loss/vent failure mode.
- Persistence should not assume a future multi-fluid state is impossible; schema versioning should allow evolution to a component list later.

### Different-Fluid Connection Policy

When two networks containing different active fluids become connected, they must not merge their fluid state.

Policy:

- If the offending connection is made by connectable pipes, the pipes should disconnect instead of creating a mixed network.
- If any other block or connection type creates the conflict and cannot be cleanly auto-disconnected, both networks should freeze.
- Frozen conflicting networks should remain blocked until the offending block or connection is removed.

Implications:

- The topology layer must be able to detect "would merge incompatible fluids" before applying state merge.
- The connection layer needs a way to reject or roll back pipe connections.
- Frozen/conflicted state is distinct from chunk-load `pending`.
- Tests must cover pipe auto-disconnect and non-pipe conflict freeze.

### Frozen Network Semantics

Frozen networks are a safety state for incompatible-fluid topology conflicts.

A frozen network:

- Does not accept fluid.
- Does not output fluid.
- Does not allow machine processes to run.
- Preserves its current fluid state.
- Remains visible in UI/WAILA and should report that it is frozen.
- Can still be repaired by removing the offending block or connection.
- Still applies passive heat loss.

Heat loss must continue while frozen to prevent exploits where a player freezes a hot network to preserve thermal energy indefinitely.

Frozen is different from pending:

- Pending means the network may be incomplete because chunks/members are not loaded.
- Frozen means the loaded topology contains an invalid incompatible-fluid connection.

### Spatial Resolution

Initial IFN redesign uses a lumped model: one thermodynamic state per connected network.

Decision:

- One network has one active fluid state.
- One network has one derived pressure and one derived temperature at a time.
- Internal gradients are out of scope for the first redesign.

Implications:

- Pressure drop, loop penalties, and heat exchange can exist as process-level calculations, but they should not require storing a per-pipe state.
- Long-distance or high-throughput penalties should be modeled analytically from topology summaries such as pipe count, loop length, module count, or resistance factors.
- Future segment/gradient models should remain possible but must not be required for the initial architecture.

### Pressure Drop Scope

Within one IFN network, assume no internal pressure drop for the first redesign.

Decision:

- A single connected network has one pressure.
- Pressure drop is a property of machines/processes, not passive transport inside one network.
- Machines can require pressure gradients between input and output networks.
- Pumps should be needed every few machines or stages to maintain useful pressure gradients.

Implications:

- IFN pipes are not a per-tick hydraulic resistance simulation.
- Process planners must account for machine pressure drop and input/output pressure constraints.
- Network topology can affect capacity and heat loss, but not internal pressure gradient in the initial model.

### Pump Scope

Pumps should be machines, not special pipe topology.

Decision:

- A pump connects an input IFN network to an output IFN network.
- It consumes energy and changes the output network state according to a process model.
- It may increase pressure, move substance, and add heat/enthalpy according to efficiency.
- It is not a passive network member that modifies pressure inside a single network.

Implications:

- Pump behavior belongs in machine process planning/execution.
- Pump support should reuse the same planner-first architecture as Heat Pump and Radiator.
- Topology remains responsible for connectivity and capacity, not for doing thermodynamic work.

### Machine Network Arity

IFN machines may operate on one network or between multiple networks.

Decision:

- Two-network machines are allowed and expected for pumps, heat pumps, exchangers, and regulators.
- Single-network machines are also allowed, such as heaters, coolers, vents, sensors, or stabilizers.
- If a machine that physically relies on a gradient is configured on one network, the natural result should be poor or zero useful work, not a special arbitrary rule.

Examples:

- Heat pump gets meaningful reward from gradients between source and sink networks.
- Pump connected to the same network on both sides performs no useful pressure work.
- Single-network heater can add thermal energy directly to one network.

Implications:

- Process planners should support one-network and multi-network requests.
- Machine behavior should emerge from state differences where practical.
- UI should make poor configurations understandable without requiring formulas.

### Pipe Materials And Heat Loss

Pipe materials should matter for thermal behavior.

Decision:

- Different pipe materials may have different thermal conductivity.
- Different pipe materials may have different maximum safe temperature.
- Different pipe types/materials may have different maximum safe pressure.
- Heat loss should include parameters for conduction, convection, and radiation.
- The first redesign should still avoid per-pipe thermodynamic state.

Implications:

- Network topology should aggregate pipe/material counts or effective surface/thermal coefficients.
- Network limits should include pipe/material pressure limits, not only accumulator/hydrophore limits.
- Passive ambient exchange should move network temperature toward ambient temperature based on delta T, regardless of whether the network is hotter or colder than ambient.
- Heat loss/gain can be computed from network temperature, ambient temperature, and aggregate material properties.
- Any passive heat exchange changes canonical energy/enthalpy and all derived properties, including pressure and phase, must be recomputed from the new state.
- Overtemperature checks should be possible at the network/material level.
- Overpressure checks should use weakest-link behavior: the weakest relevant member sets the safe pressure limit for the whole lumped network.
- Radiation may require a nonlinear term, but it should be implemented with a cheap analytical formula and bounded inputs.
- The UI can expose simplified warnings such as "high conductive loss", "poor insulation", "radiative loss high", or "pipe material temperature limit exceeded".

Pressure limit decision:

- Pipe/member pressure safety should be based on maximum pressure differential, not simple maximum absolute pressure.
- The safe pressure differential of a network is limited by the weakest pipe/member in that network.
- This is intentionally simple and deterministic for the initial one-state network model.

Temperature limit decision:

- Maximum operating temperature also uses weakest-link behavior.
- The lowest relevant member temperature limit sets the safe operating temperature for the whole lumped network.

Failure threshold:

- If pressure or temperature exceeds maximum safe operating parameters by 10% or more, one of the weakest matching pipes should rupture/explode and the network should be emptied.
- The ruptured pipe should be selected from the weakest relevant pipe set, not an arbitrary strong pipe.
- This should be an explicit failure event, not a hidden side effect of a read-only query.
- Rupture voids the entire network contents, including stored substance and thermal energy.

Overload band below hard failure:

- If pressure or temperature is above the safe operating limit but less than 10% above it, the network continues operating with a warning.
- If that warning condition persists for at least 200 ticks, the network gains a 1% chance every 20 ticks to rupture/explode one of the weakest matching pipes.
- This requires tracking overload duration separately for pressure and/or temperature.
- The warning and risk state should be visible in UI/WAILA.
- Overload timers reset immediately when the network returns to safe operating range.
- Runtime randomness does not need special player-visible determinism, but tests should be able to inject a seeded or fake RNG for failure checks.

Future safety devices:

- Add valves, relief valves, rupture disks, or safety devices later to localize, vent, or prevent catastrophic failures.
- These are a good future design direction but are not required for the initial core model.

### Phase Transition Consequences

Phase transition is not a failure by itself.

Decision:

- Changing phase should be a normal part of the thermodynamic model.
- Phase transitions can be useful for cycles, heat pumps, cooling, steam behavior, and other advanced setups.
- Bad outcomes should come from the resulting network parameters, not from a special "phase changed" punishment.

Examples:

- If boiling causes pressure to exceed the network's operating limits, the overpressure rules apply.
- If condensation changes amount, volume, or temperature in a way that reduces throughput, that emerges from the process model.
- If a machine cannot operate across a phase window, that should be a machine or process constraint, not a global network failure.

Terminology note:

- Prefer "network operating limits" or "parameter limits" over vague "failure policy" when discussing these rules.

### Two-Phase State

Two-phase fluid state is allowed and should be treated as a normal IFN state.

Decision:

- The network model should support two-phase regions instead of collapsing everything to a dominant liquid/gas label.
- A derived quality or vapor fraction may be needed for calculations and UI.
- Two-phase behavior should still use cheap analytical approximations.

Implications:

- Phase output should distinguish at least liquid, vapor, supercritical, and two-phase.
- Machine process constraints may depend on phase or quality.
- Pressure and temperature calculations must remain deterministic in the two-phase region.
- The initial two-phase model can be an approximation. More accurate saturation curves or tables can be added later if the approximation is not good enough.

### Initial Fluid Coverage

Initial calibrated fluids should be common, well-known substances that exist in the game and have easy-to-find physical parameters.

Priority examples:

- Water/steam.
- Hydrogen.
- Helium.
- Oxygen.
- Nitrogen.

Implications:

- The fluid property registry should start with a small, reliable set instead of pretending every mod fluid is physically calibrated.
- Tests should run the same invariant suite across several fluids in parallel or through parameterized cases to expose edge cases.
- Unknown or uncalibrated fluids must not enter IFN.
- IFN fluid support is opt-in through calibrated fluid properties, not fallback-based.

### Fluid Property Registration

IFN needs an explicit fluid registration mechanism.

Decision:

- Fluids should be registered into IFN support with calibrated physical/gameplay parameters.
- The registration API can be a builder, registry, or similar pattern.
- The registry is the single source of truth for whether a fluid is IFN-compatible.

Implications:

- Fluid properties should not be scattered as ad hoc constants across machines.
- Tests should be able to register test fluids with controlled properties.
- The registry should make it obvious which required properties are missing.

### Fluid Property Shape

Single scalar values such as boiling point, critical temperature, and critical pressure are probably not the right primary representation.

Preferred direction:

- Use an approximated phase diagram or phase-boundary model for each calibrated fluid.
- Derive phase behavior from the phase model instead of hardcoding one boiling point.
- Keep the phase model cheap, analytical, and deterministic.

Implications:

- The registry should accept a compact phase model, not only scalar constants.
- Critical point and boiling behavior may still exist as parameters inside a specific approximation, but they should not be the whole public model.
- Tests should cover phase classification across representative pressure/energy/temperature regions for each registered fluid.

### State Solver And Fluid Property Layering

Use a layered model for extensibility.

Decision:

1. Canonical IFN state stores fluid ID, conserved substance amount, energy-like state, and network volume from topology.
2. A state solver computes a solved state from canonical state, fluid properties, and network limits.
3. Fluid property models provide cheap phase/property functions such as phase at pressure/temperature, density, and energy relationships.

Recommended API shape:

- `solve(canonicalState, fluidProperties, networkLimits) -> solvedState`
- fluid properties may internally expose functions like `phaseAt(P, T)`, `densityAt(P, T)`, and `specificEnergyAt(P, T, phase)`

Implications:

- The network does not know how phase diagrams are represented.
- The fluid registry does not know network topology.
- Machines consume solved state and process planner results, not raw phase-diagram internals.
- More accurate fluid models can be added later for selected fluids without rewriting machine logic.

Canonical energy decision:

- Use total internal energy as the canonical energy-like state.
- Enthalpy should be a derived/solved value used by flow and machine process calculations.

Reasoning:

- A network is modeled as a lumped volume, so internal energy fits the stored state better than enthalpy.
- Pressure and temperature should be solved from substance amount, internal energy, and network volume.
- Enthalpy remains important for flowing processes but should not be independently persisted as the network's canonical energy.

### Network Volume And Hydrophores

The current base capacity plus accumulator capacity split was introduced quickly to handle pressure spikes in liquid-filled pipes.

Design intent:

- A hydrophore/accumulator concept feels natural and should remain as a formal part of the design.
- The first redesign should revisit the volume model instead of blindly preserving the current implementation.

Open design issue:

- Decide whether the canonical solver needs one total volume, or separate medium volume plus compliance/accumulator volume.
- The answer may differ for liquids and gases.

Implications:

- The old `baseCapacity + accumulatorCapacity` model is not automatically final.
- The redesigned model should explain how hydrophores reduce pressure spikes without becoming an exploit or arbitrary magic buffer.

Hydrophore direction:

- A hydrophore should be a network member/block.
- It should expose adjustable pressure parameters, representing the amount or preload of gas cushion in the hydrophore.
- It is not just a larger fluid tank. Its purpose is pressure compliance and spike damping.
- The hydrophore gas cushion should be modeled as an abstract compliance/preload parameter, not as a concrete simulated gas medium in the first design.
- Hydrophores should initially affect all media, not only liquids.
- If this significantly complicates the solver or produces poor gameplay, revisit the rule later.

### Low Pressure And Vacuum

IFN should support low pressure and vacuum-like operation as normal gameplay states.

Decision:

- Networks may operate below 1 bar.
- Lower pressure should become progressively harder to achieve, similar to real systems.
- Pumps or vacuum machines should have increasing energy/capability requirements as target pressure decreases.

Implications:

- Pressure models must not clamp all valid states to 1 bar.
- There should still be a safe numeric lower bound to avoid divide-by-zero and unstable calculations.
- UI should communicate vacuum difficulty and target pressure clearly.
- Pipe/member safety should account for pressure differential against ambient/external pressure, so both overpressure and vacuum collapse can be represented.

### Ambient Temperature And Pressure

Ambient temperature and ambient pressure should initially be per dimension.

Decision:

- Each dimension can define a baseline ambient temperature.
- Each dimension can define a baseline ambient pressure.
- Per-biome temperature may be added later.

Implications:

- Ambient conditions should be provided by a model/service, not hardcoded inside network state.
- Heat loss code should accept ambient temperature as an input.
- Pressure differential checks should accept ambient pressure as an input.
- Persistence should not store ambient-derived values.
- Future biome support should not require rewriting IFN state.
- Ambient conditions should be available to machine process planning as part of process context because network parameters and machine behavior are coupled.
- Machines should preferably receive ambient context through planner/process inputs instead of reading world conditions ad hoc in many places.
## Current Machine UX Observations

Current IFN-facing machines already point toward a controller-style UX rather than a purely passive pipe system.

- `MTEHeatPump` exposes explicit operating modes:
  - target temperature,
  - target COP,
  - target energy per tick.
- The heat pump also exposes tuning controls for:
  - fluid amount per operation,
  - lower/upper temperature tolerance,
  - hot/cold stream selection,
  - split-flow mode,
  - split ratio,
  - heat-exchanger mode,
  - heating/cooling direction.
- The heat pump GUI is diagnostic-heavy: it shows mode, warnings, target value, input/output temperatures, network info, COP/effective COP and energy cost.
- `MTERadiator` has simpler but similar control shape:
  - constant-time mode,
  - target-temperature mode,
  - configurable operation ticks,
  - configurable target temperature.
- Radiator UI also surfaces machine/network diagnostics: loop status, segment/module counts, pressure drop, outlet pressure, input/output temperatures and network info.

Design implication:

- Existing UX already assumes players can set explicit process goals and inspect the result.
- Redesign should probably keep this spirit, but move machine behavior onto cleaner process abstractions:
  - machine reads solved network states,
  - machine computes whether requested setpoint is physically/economically possible,
  - machine reports reasoned diagnostics,
  - machine applies a deterministic transfer only after validation.
- Current implementation includes some bounded estimate loops in heat-pump target-energy logic; future solver design should prefer analytical formulas and fixed deterministic calculations where possible.

Clarification:

- Current machine code must not be treated as an architectural quality baseline.
- The current machines are useful mainly as a gameplay-intent signal:
  - player-facing mechanics should support configurable process goals,
  - machines should expose meaningful diagnostics,
  - advanced tuning should create optimization space.
- The redesign is free to replace the internals, APIs and process model instead of preserving the current implementation shape.

Machine control model:

- IFN machines should not be forced into one universal UX/control pattern.
- Declarative and mechanical controls are both valid depending on the machine and selected mode.
- Example:
  - heat pump: target/setpoint-style operation makes sense; constant-time operation generally does not.
  - radiator: constant-time and target-temperature operation can both make sense.
- Machine definitions should therefore declare their supported control modes and process semantics explicitly.
- The common architecture should provide shared validation, diagnostics, transfer planning and state application, not a single hardcoded control philosophy.

Process timing:

- IFN machines may operate either as continuous/tick-based processes or as batch processes.
- The architecture must support both:
  - small deterministic per-tick transfers,
  - accumulated/conditioned operations that execute after N ticks or after reaching a required state.
- Machine process definitions should own their timing semantics.
- The shared transfer/state layer should not assume every machine performs the same amount of work every tick.

Batch process resource ownership:

- For batch processes, the preferred model is not "observe the network and commit at the end".
- A batch machine should generally consume/reserve required input immediately, store it in explicit machine-local thermodynamic state, perform its internal process, then attempt to output the result at the end.
- Reason: while a process is running, other machines may change the source network state. Delayed input consumption would make the process race-prone and physically misleading.
- Machine-local thermodynamic state must be explicit, persisted and visible enough for diagnostics.
- IFN should not hide partially completed transfers inside network state.

Batch output backpressure:

- If a batch process finishes but cannot output its product, the machine keeps the product in local state.
- The machine repeatedly tries to push output whenever the destination becomes valid.
- Partial output is allowed if the machine/output semantics support it.
- The machine must not start a new process while finished output remains blocked.
- Blocked output should be visible in diagnostics/UI.

Batch local-state maintenance:

- A machine-local batch is not assumed to be perfectly static or free.
- The machine should actively work to keep the internal batch at requested parameters when its process semantics require that.
- Maintaining internal temperature/pressure/etc. may consume energy or impose losses.
- This creates gameplay cost for holding blocked/unfinished products instead of making machine-local state an exploit-proof perfect buffer.
- Exact heat loss/maintenance behavior may differ per machine, but the architecture must support local-state evolution over time.

Batch maintenance failure:

- If a machine cannot maintain the requested local batch parameters, the batch should drift according to physical/environmental behavior.
- Default behavior is not an immediate hard fault.
- Drift may change process efficiency, final output state, eligibility for output, or eventually trigger machine/network safety consequences depending on the machine and medium.

Machine-local state is not storage:

- IFN machines should not gain extra hidden intermediate tanks/containers as part of the redesign.
- Machines take input from a hatch/input boundary and output to another hatch/output boundary.
- Any batch-local thermodynamic state is an active in-process charge, not a general-purpose storage volume.
- It should exist only while the machine is processing or waiting to output a completed process.
- It should be abstract and machine-owned, with explicit persistence/diagnostics, but not usable as player-facing fluid storage.

Hatches:

- IFN hatches are part of the IFN network.
- A machine takes input from an input-side IFN network through its hatch and outputs to an output-side IFN network through another hatch/boundary.
- Hatches should therefore participate in topology, limits, diagnostics and solved network state like other network members.
- The machine's active batch/process state is distinct from hatch/network state.
- IFN hatches do not impose their own temperature or pressure limits.
- Pressure/temperature safety limits come from relevant pipes/members/material constraints, not from the hatch itself.
- The machine itself is not part of the IFN network.
- Machines operate on connected IFN networks through hatches/ports, but do not add network volume/capacity or pipe-like safety limits.
- Every IFN infrastructure member contributes network volume/capacity.
- Current target scale:
  - pipe segment: about 100 L,
  - hatch: about 10000 L,
  - hydrophore: effective 0 L to 10000 L depending on pressure/configuration.
- These values are design targets and may be moved into explicit member definitions/configs during redesign.
- Hydrophore effective capacity/compliance updates immediately when its configuration changes.
- Hydrophore is not initially modeled as a delayed mechanical process.
- Hydrophore is passive: it does not add energy, remove energy or perform useful work on the medium.
- It acts as an immediate pressure-smoothing/compliance element.
- Changing hydrophore effective capacity preserves conserved substance amount and internal energy; the network solver recomputes pressure, temperature and phase from the new volume/capacity.

Pumps:

- IFN pumps are ordinary process machines, not special topology elements.
- A pump transfers medium from an input IFN network to an output IFN network.
- Its defining behavior is pushing above the input network pressure, consuming energy to create/maintain a pressure increase.
- Pump behavior should be implemented through the shared machine/process/transfer API.
- Pump should support multiple control modes:
  - target output pressure,
  - target pressure differential,
  - target flow.
- These are player-selectable modes, not separate machine classes.
- If a pump cannot reach its requested mode target under current conditions, it should operate at the maximum achievable effect instead of simply stopping.
- The pump does not need to report an abstract "cannot reach setpoint" reason.
- It should expose concrete current operating values where useful, e.g. actual flow, actual pressure change, energy use, input/output states.

Machine/network transfer limits:

- Machine flow/transfer is limited both by the machine itself and by connected networks.
- Input network availability can limit how much the machine can take.
- Output network acceptance can limit how much the machine can push.
- The effective transfer is the valid amount after applying machine limits and network-side constraints.
- Output backpressure naturally limits normal machine/pump flow.
- Finished batch/backlog output is an active machine process, not passive equalization from a hidden tank.
- A machine should be able to actively push compatible backlog output in portions when the destination can accept it, instead of letting backlog pressure decay in a way that self-blocks unloading.
- Not every machine can actively push against pressure.
- Many process machines, e.g. heat pumps/radiators/heat exchangers, should impose pressure loss rather than pressure gain.
- Example rule: if a heat pump input is at 1.00 bar, its output cannot exceed roughly `1.00 bar - pressureLoss` unless another machine/pump provides pressure.
- Pressure-raising behavior belongs to pumps or other explicitly pressure-adding machines.
- Backpressure blocks or limits such process-machine flow when the destination pressure is too high relative to the input-side pressure minus machine loss.
- For ordinary process machines, pressure loss can initially be modeled as a constant value defined by the machine.
- Open design detail: heating/compressibility changes specific volume. Example: a heat pump that heats gas increases its energy and would tend to increase pressure unless the gas expands. Therefore the output-side acceptance calculation must account for the outlet pressure limit and solved downstream state, not just transfer the same "volume" blindly.
- This reinforces that IFN transfers should move conserved substance and energy; actual occupied volume/pressure are solved from the destination network state.

Flow units:

- Player-facing machine flow should represent current/actual volume flow in the relevant process context.
- Backend transfer must convert that volume request into conserved substance amount and energy using the current solved state/fluid density.
- Backend may quantize/round to a reference mB/substance unit, but the conserved quantity is substance, not expanded current volume.
- Extraction must be limited by density/available substance and pressure behavior.
- Important anti-exploit requirement: pumping the last apparent `1000 mB` of current volume from a network must not produce a free perfect vacuum or duplicate usable volume.
- Vacuum should be approachable only through the pressure/amount/energy model and machine limits/costs, not by draining one final UI-volume unit.

Pump and turbine machine families:

- There should be at least two gas pump profiles:
  - normal gas pump,
  - vacuum gas pump.
- Liquid and gas pumps may be cross-usable with penalties instead of hard-blocked, subject to later tuning.
- The turbine should be one generalized machine family for energy recovery/extraction from pressure drop/expansion.
- It should not initially be split into separate "gas turbine" and "liquid turbine" concepts.
- Phase/medium should influence turbine efficiency, limits and valid operating envelope.
- Turbine operates from pressure differential.
- It computes flow from `dP` rather than forcing a target flow like a pump.
- Turbine energy output derives from the pressure drop/expansion process and machine efficiency.
- Turbine should not directly apply an arbitrary temperature penalty.
- Outlet temperature should result from expansion/energy balance in the solver.
- Future turbine blade variants may define optimal operating envelopes for temperature, pressure, phase and/or medium.
- Blade envelopes can become progression/optimization milestones: players may need to achieve certain thermodynamic conditions to use better blades efficiently.
- Turbine blades should have durability.
- Blade variants should define optimal operating parameters/envelopes.
- Operating outside the optimal envelope may reduce efficiency, throughput and/or increase durability damage.
- Two-phase formation during expansion, e.g. condensation, should apply a large durability penalty to blades.
- This gives players a strong reason to control inlet conditions, pressure ratio and outlet state rather than only maximizing `dP`.
- Turbine is the main/only planned IFN machine family with consumable durability/wear mechanics.
- Other machines such as pumps, heat pumps and radiators are intended to be mostly passive/stable after construction.
- Their cost comes from energy use, limits, pressure loss, infrastructure requirements and operating conditions rather than part wear.

Pipe durability:

- IFN pipes should not have long-term durability/fatigue wear.
- Pipes have safety limits and overload/failure events, but no gradual wear mechanic.

Turbine blade representation:

- Turbine blades should be item components inside the turbine machine.
- Blade durability/wear should be represented on the item.
- Players replace blades as items rather than rebuilding turbine structure blocks.
- When a blade fully fails, it breaks/disappears.
- A broken/no-blade turbine should effectively connect/bypass the input and output networks, dumping the pressure gradient instead of producing controlled energy.
- This should not be modeled as normal productive turbine operation.
- The failure is a major system consequence: players lose the maintained gradient and may trigger downstream safety effects.
- The bypass/no-blade state persists until a new blade item is inserted.
- No-blade/broken-blade bypass should behave like a low-resistance connection with a small pressure loss.
- No-blade/broken-blade bypass is modeled as an active machine transfer between input and output ports, not as topological network merge.
- While the turbine is enabled without valid blades, it takes medium from the input network and pushes it to the output network with the bypass pressure loss.
- Disabling the turbine stops the no-blade/broken-blade bypass transfer.

Machine enabled state:

- A disabled/off IFN machine does not operate.
- Disabled machines do not perform process transfers, productive operation, or bypass transfers.
- For process purposes, disabled machines block their machine-mediated flow regardless of blade state or other configuration.

Automation/control:

- Machine enable/disable control should use redstone.
- IFN should add sensor/controller blocks that emit redstone based on network parameters.
- Sensor examples may include pressure, temperature, phase, overload warning, frozen state, fill/amount, or other solved-state diagnostics.
- IFN does not initially need a separate custom signal bus if redstone sensors can expose the needed automation hooks.
- IFN sensors should be implemented as GT-style covers, not standalone blocks initially.
- Example: `Pressure Detector Cover`.
- Covers are attached to relevant IFN members and configured with conditions such as pressure greater than/less than a threshold or within/outside a range.
- Covers emit redstone based on the configured condition.
- Initial required sensor covers:
  - pressure detector,
  - temperature detector.
- Other covers such as phase, amount/fill, frozen, overload can be deferred.
- Detector covers should support selectable output modes:
  - binary 0/15,
  - linear/scaled redstone.
- Default mode should be binary 0/15.
- Detector covers read the solved state of the whole connected IFN network.
- There is no local per-pipe pressure/temperature in the initial lumped model.
- The cover's physical location controls attachment/redstone output, not a local thermodynamic sample.
- Linear detector mode maps a player-configured `[min, max]` parameter range to redstone `0..15`.
- Pressure detector should support pressure source modes:
  - absolute network pressure,
  - pressure differential relative to ambient/dimension pressure.
- Temperature detector should support temperature source modes:
  - absolute network temperature,
  - temperature delta relative to ambient/dimension temperature.
- Temperature should be displayed/configured in Kelvin for IFN UI/config.
- Pressure should be displayed/configured in bar for IFN UI/config.
- Volume and flow should be displayed/configured as liters and L/t for IFN UI/config.
- Energy/power exposed to players should use EU/EU-tick terminology consistent with GT.
- IFN backend should also use EU as the canonical energy unit.
- Physical property data may be sourced from real-world values, but converted/calibrated into the GT/IFN EU scale.
- There is no fixed real-world Joule-to-EU conversion.
- Real thermodynamics should inform relationships/shapes, but absolute energy magnitudes are calibrated for GT gameplay balance.
- IFN fluid property definitions should be authored directly in IFN/GT units.
- Helper spreadsheets/scripts may be useful offline, but the runtime registry should not depend on real SI-to-EU conversion.

Balance configuration:

- IFN should not rely on broad global balance multipliers such as universal heat-loss, pump-cost or turbine-output multipliers.
- Balance should primarily live in concrete definitions:
  - fluid property records,
  - machine parameters,
  - pipe/material parameters,
  - turbine blade profiles.
- IFN definitions should initially be hardcoded through Java builders/registries rather than loaded from external JSON/config.
- This applies to calibrated fluid properties and machine/material/blade definitions unless a later requirement changes it.
- The IFN fluid registry should initially be closed/controlled.
- Only explicitly registered and calibrated fluids are IFN-compatible.
- Public addon/API extension support is not required for the initial redesign.
- Ordinary GT fluids not present in the IFN registry are ignored by IFN input.
- IFN should not accept or convert unregistered fluids.
- Unsupported/unregistered fluid rejection can be silent for now; no extra UI message is required initially.

Core solver responsibility split:

- IFN core should separate pure state solving from runtime side effects.
- `StateSolver` should be a pure deterministic physical function:
  - input: canonical state such as fluid id, conserved substance amount, internal energy, network volume/capacity, ambient/context where needed,
  - output: solved state such as pressure, temperature, phase, quality, density, occupied volume, diagnostics.
- Safety/rule evaluation should be separate:
  - checks pressure/temperature limits,
  - computes warnings, overload timers/risk inputs,
  - identifies weakest-link constraints.
- Runtime/tick layer should apply side effects:
  - heat loss,
  - overload timers,
  - rupture/failure events,
  - voiding contents,
  - persistence updates.
- Machine process layer should plan and validate transfers using solved states, then apply canonical state changes atomically.

Canonical numeric representation:

- Conserved substance amount should be stored as an integer/fixed-point quantity, not as float/double.
- This is intended to prevent drift, duplication and rounding loss across repeated transfers.
- Floating-point calculations may be used in pure solver/derived calculations, but committed canonical amount changes should return to the fixed-point representation.
- Conserved internal energy should also be stored as integer/fixed-point EU.
- Solver calculations may use floating point internally, but committed energy changes should be rounded back into the canonical fixed-point EU representation.
- Canonical conserved substance quantity should be named `substanceAmount`.
- In IFN context, `substanceAmount` means conserved substance units, not current physical volume in liters.
- UI volume/flow values are derived from solved density/state and should not be confused with canonical `substanceAmount` or GT `FluidStack.amount`.
- Network/member volume should use a fixed-point representation, not plain integer liters.
- Player-facing UI can still display liters/L/t.

Value types and solved state caching:

- IFN should prefer separate value types/wrappers for major units to prevent accidental mixing:
  - `SubstanceAmount`,
  - `EnergyAmount`,
  - `VolumeAmount`,
  - `Pressure`,
  - `Temperature`.
- Canonical state remains the only source of truth.
- Solved pressure/temperature/phase/etc. are derived values.
- Derived solved state may be cached as a runtime optimization, but must be invalidated whenever canonical state or topology-derived volume changes.
- Heat loss will usually invalidate solved state every tick.
- Cache is useful mainly within a tick or between reads when no canonical changes occurred.
- Derived solved state should not be persisted as authoritative NBT data.

Empty networks and trace amounts:

- When a network reaches exactly zero `substanceAmount`, it becomes empty:
  - no active fluid/material,
  - zero internal energy.
- Networks should keep trace/nonzero `substanceAmount` values down to the fixed-point minimum rather than auto-voiding them.
- Reason: low-pressure/vacuum gameplay requires representing very small nonzero amounts.
- UI may display trace/near-empty states in a simplified way, but canonical state should not be silently rounded to empty.
- Empty network UI can report ambient temperature/pressure for readability.
- Ambient display for empty networks is not a persisted canonical thermodynamic state.
- Heat loss on an exactly empty network has no effect because there is no substance/energy to evolve.

Passive heat exchange:

- Passive heat exchange with ambient changes network internal energy, not `substanceAmount`.
- It should move the solved temperature toward ambient based on `dT` and should not overshoot ambient.
- Heat loss/gain should be computed from aggregated network infrastructure:
  - pipe count/material/type contributions,
  - hatch contributions.
- Aggregation can use weighted-average/effective coefficients rather than per-pipe thermal state.
- Initial model should not require local per-element temperatures.
- Radiator is a machine, not a passive infrastructure member of the IFN network.
- Radiator has input, operates on a batch, then outputs the processed medium.
- Its internal process is heat exchange between the batch and ambient through a modular structure.
- Current radiator behavior drains from the input network and immediately adds the processed state to the output network while setting machine progress/time/EU usage.
- Design intent to preserve:
  - radiator behaves like batch/recipe-style processing from input to output,
  - it can run in target-temperature mode or fixed-time mode,
  - output acceptance can limit processed amount,
  - pressure drop applies across the radiator loop.
- Redesign should improve this into explicit batch/process lifecycle rather than hidden immediate mutation inside recipe checking.
- Recommended redesign:
  - radiator starts by taking a real batch from the input network,
  - keeps it as explicit active process state,
  - evolves it over process time using ambient heat exchange through the modular structure,
  - then outputs the resulting state to the output network,
  - waits with finished output if the output network cannot accept it.
- Radiator should not consume EU.
- Radiator operation is driven/limited by pressure drop/flow conditions, not electrical power.
- Target-temperature radiator mode:
  - takes a batch,
  - keeps it in active processing until it reaches the target temperature,
  - then moves it to finished output state and attempts to output it.
- Important batch lifecycle correction:
  - active process state may evolve under the machine's physics,
  - finished output state should not continue being processed by that machine's main physics.
- This prevents target-temperature processes from overshooting when output is temporarily blocked.
- Global default:
  - active batch/process state is affected by machine physics,
  - finished output state waits for transfer and is not further processed,
  - finished output blocks starting a new process,
  - finished output is stable by default on start, with no generic drift.
- This is a deliberate gameplay/automation simplification.
- Future machines may define exceptions explicitly if needed.
- Active batch/process state and finished output state must be persisted.
- Chunk unload/reload must not reset machine-local thermodynamic process state or lose/duplicate contents.
- IFN machines and networks never catch up offline/unloaded time.
- They only evolve while actually ticked/loaded.

Chunk loading:

- A partially loaded/incomplete network should enter pending/incomplete state rather than behaving as independent temporary split networks.
- Pending networks should prevent unsafe transfer/process behavior until topology/state is complete again.
- This avoids chunk-boundary split/merge exploits.
- Pending behaves similarly to frozen for transfer/process purposes:
  - no input,
  - no output,
  - no machine operation using that network,
  - state is preserved and visible.
- Difference:
  - pending is caused by incomplete loading/topology availability,
  - frozen is caused by invalid/conflicting network topology or fluid conflict.
- Frozen networks still apply passive heat loss to prevent exploits from freezing hot/cold contents.
- Pending networks should not apply passive heat loss while topology is incomplete, because volume/surface/thermal aggregates may be unknown or partial.
- When pending resolves and the full topology is available again, the network should rebuild topology aggregates, invalidate/solve derived state, and resume normal operation without additional penalty.
- If pending resolves and full rebuild shows that topology actually changed, e.g. a pipe/hatch was destroyed while unloaded, treat it as a normal topology change/split/merge event.
- Do not assume the previous network must continue 1:1 if the world state no longer contains the same members.
- This is considered an extreme edge case, not the primary gameplay path.

Topology split:

- When one lumped IFN network splits into multiple networks, distribute canonical `substanceAmount` and internal energy proportionally to resulting network volumes.
- Goal: preserve equal/consistent pressure/state across resulting sections as much as the lumped model allows.
- Because canonical quantities are integer/fixed-point, split rounding must be deterministic.
- Rounding loss is acceptable/expected; splitting should never create extra `substanceAmount` or energy.

Topology merge:

- When compatible networks merge, sum canonical state:
  - `substanceAmount` adds,
  - internal energy adds,
  - topology-derived volume/capacity adds/rebuilds.
- Then solve the new derived state from canonical values.
- If the resulting state exceeds safety limits, normal warning/rupture/failure rules apply after merge.
- Empty + non-empty merge is compatible because empty has no active fluid/material.
- The merged network uses the non-empty fluid/material and canonical substance/energy, but with the rebuilt combined volume.
- Solver recomputes state from the new volume, so gas expansion/compression effects are possible.
- Non-empty networks with different fluids/materials are incompatible.
- If an incompatible connection is caused by a simple pipe/pipe-style connection that can be prevented, the connection should be disallowed/disconnected and both networks remain normal.
- If the incompatible connection cannot be safely auto-prevented, e.g. via conflicting hatches/blocks, fallback to frozen state for affected networks until the offending connection/block is removed.
- Frozen fallback applies to both affected networks involved in the incompatible connection.
- Frozen diagnostics should show at least the reason, e.g. incompatible connection/fluid conflict.
- If cheaply available, UI/WAILA should also show one offending block/connection position to help players repair the network.

Safety warning UX:

- When a network is over safe operating limits but below immediate rupture threshold, UI/WAILA should show a simple over-limit warning.
- Do not expose exact overload timers, rupture probabilities or edgeable thresholds to the player.
- Reason: players should respond to unsafe operation rather than intentionally riding hidden failure timers.
- Detector covers expose raw pressure/temperature values, not special over-limit status.
- Players can build their own safety automation from raw values and known pipe limits.
- Pipe item/tooltips should show that pipe's own stats, such as max temperature and max pressure differential.
- Built network WAILA should show aggregate/weakest-link limits for the whole network rather than only the specific looked-at element.

WAILA / in-world network diagnostics:

- Current IFN WAILA shows useful gameplay-facing information:
  - fluid name,
  - stored/current amount versus capacity,
  - capacity breakdown including hydrophore/accumulator capacity,
  - network member count,
  - pipe count on pipes,
  - pressure,
  - temperature,
  - heat loss and `dT`,
  - relative specific enthalpy,
  - phase and two-phase quality,
  - simple connection/pressure warnings on specific hatch types.
- Redesign should keep the useful intent but align terminology with the new model.
- Recommended WAILA for any IFN network member:
  - network status: normal / pending / frozen,
  - fluid/material or empty,
  - reference liters as the player-facing conserved amount of substance,
  - current occupied volume where meaningful and total network volume/capacity in L,
  - pressure in bar,
  - temperature in K,
  - phase; include vapor quality for two-phase state,
  - passive heat exchange rate in EU/t and `dT` where meaningful,
  - network members / pipe count as practical diagnostics,
  - weakest-link max temperature and max pressure differential for the whole network,
  - simple over-limit warning when applicable.
- Do not show raw fixed-point internals such as `substanceAmount` scale values.
- Avoid the old `Std Amount` label; if a conserved quantity must be shown later, name it clearly and intentionally.
- Hatches and pipes should read the same network-level solved state; they should not display local thermodynamic state.
- Specific enthalpy should not be shown in normal WAILA; reserve it for debug/dev tooling if needed.
- IFN UI text should be in English.
- IFN should use reference liters broadly as the visible marker of the "true" conserved amount of substance.
- The displayed unit should be `refL`, written together.
- Example label/value style: `Substance: X refL`.
- Reference liters are derived from canonical `substanceAmount`, not from current occupied volume.
- `refL` is effectively a mole-like IFN amount-of-substance unit.
- It is not physical volume at a reference pressure/temperature.
- Heating, cooling, expansion and compression must not create or destroy `refL`.
- Boundary conversion to/from ordinary GT fluids should be defined explicitly from conserved `substanceAmount`/`refL`, not from current occupied volume.
- Ordinary GT fluid entering IFN converts at `1 L -> 1 refL` for supported IFN fluids.
- IFN output to ordinary GT fluid converts at `1 refL -> 1 L`.
- This is the core anti-duplication/anti-voiding boundary rule: thermodynamic state changes do not change extractable matter amount.
- This conversion is not per-fluid initially.
- For gases, occupied volume is effectively the full network volume because gas fills the available pipe/network volume; reference liters carry the meaningful conserved quantity.
- For liquids/two-phase states, occupied/current volume may still be useful, but it must remain distinct from reference liters.
- WAILA should use phase-aware volume display:
  - Always show `Substance: X refL`.
  - Always show `Network Volume: Y L`.
  - For gas/supercritical states, avoid generic `Occupied` as a fill metric; gas effectively fills the network volume.
  - For liquid states, show `Liquid Volume: X L`.
  - For multi-phase states, show relevant phase volumes, e.g. `Liquid Volume: X L`, `Vapor Volume: Y L`, and phase fraction/quality where meaningful.
- This avoids confusing conserved matter amount with current thermodynamic volume.
- Two-phase `Quality` should mean vapor substance fraction:
  - `vapor refL / total refL`.
- It is not a vapor volume fraction.
- Two-phase/multi-phase WAILA does not need separate per-phase substance lines initially.
- Total `Substance`, phase fraction/quality, and relevant phase volumes are enough.
- Phase labels/components should include at least:
  - `Liquid`,
  - `Gas`/`Vapor`,
  - `Supercritical`,
  - `Solid`.
- The phase model should not be unnecessarily limited to a rigid enum if a stable solver can represent phase composition.
- Multi-phase states such as `Liquid + Vapor`, `Solid + Liquid`, or other stable combinations are acceptable if the model remains deterministic and performant.
- Initial implementation may support a smaller stable subset, but the architecture should not block richer phase composition later.
- `Solid` is a problem/failure-like phase for IFN fluid handling, but the exact mechanics are not designed yet.
- Solid/frozen contents should ideally be recoverable rather than immediate void/destruction.
- Possible future recovery direction: warm the frozen section/network by running hot fluid/processes through connected infrastructure.
- When network contents are fully in `Solid` phase, fluid transfer/machine fluid movement should be blocked.
- For multi-phase states containing solid plus flowable phases, flow should be reduced proportionally to the flowable/non-solid fraction rather than fully blocked.
- There is no modeled flow inside a lumped network.
- Solid fraction does not create local pipe resistance in the network model.
- Instead, machines cannot extract solid material from pipes; their available batch/input amount is limited to the flowable fraction of the network contents.
- Architecture should prepare for per-phase extraction:
  - solved state can expose phase composition,
  - machine extraction can target only flowable phase components,
  - solid component remains in the network.
- Initial solver may use a simpler average-state approximation if full per-phase energy accounting is too expensive or unstable.
- `Solid` support is future-compatible design, not necessarily first implementation scope.
- Initial implementation may focus on stable liquid/gas/two-phase/supercritical behavior while leaving room for solid later.
- Thermal interaction should remain possible so the solid state can be recovered by heating.
- Use `Solid` terminology for phase; avoid overloading `Frozen`, which already describes invalid network topology state.
- Solid contents still participate in passive heat exchange with ambient.
- Do not add a special heat-pump mode that heats a solid network without flow.
- If a network fully solidifies and no existing passive/ambient thermal path can bring it back, the system is effectively dead until the player rebuilds/intervenes through normal mechanics.
- If a pipe/network containing solidified contents is broken/ruptured, contents are voided.
- Do not create separate frozen-fluid item/block drops initially.
- Solid phase does not directly damage pipes by freezing/expansion initially.
- Its primary mechanical consequence is blocking fluid transfer/flow.
- When solved phase leaves `Solid` after heating, flow/machine transfer can resume automatically.
- Current radiator code supports the gameplay idea:
  - modular loop/structure,
  - conduction modules,
  - heat-exchange modules,
  - effective conductance from module branches,
  - radiative contribution dependent on temperature.
- Redesign should keep the modular-structure intent while implementing radiator as a clean machine process with explicit batch state and output behavior.

Machine IFN ports:

- IFN machines must not be restricted to one input network and one output network.
- Machines may have multiple IFN ports connected to multiple independent networks.
- Current heat pump gameplay already implies up to four different IFN networks.
- The process API should model named/typed ports rather than hardcoding input/output pairs.
- Each hatch/port has a fixed direction: input or output.
- A single hatch should not act as both input and output.
- Machine modes may decide which ports are used, but should not reverse a hatch's declared direction.
- Hatches should be colorable with spray cans for UX/identification.
- This is especially important for machines with many IFN ports, e.g. a four-port heat pump where players may label network pairs such as A->C and B->D.

Output mixing:

- Output pushed from a machine into an existing IFN network mixes with the destination network.
- The resulting network state should be computed by conservative weighted averaging:
  - conserved substance amounts add,
  - internal energies add,
  - derived temperature/pressure/phase are recomputed from the new canonical state.
- Output should not overwrite the destination network state.
- Output to a network with a different fluid/material is blocked.
- Different phase of the same fluid/material is acceptable.
- Phase is a solved thermodynamic result, not a separate fluid identity for compatibility purposes.
- Output to an empty IFN network initializes the network by adding the incoming conserved substance and energy to that network's volume/capacity, then solving the resulting state.
- The empty destination network does not simply copy the machine/product pressure and temperature.
- Example: gas output into a larger empty network should expand; pressure and temperature may drop according to the fluid model and conserved energy.
