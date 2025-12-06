# NetworkManager Usage Examples

This document provides practical examples of how to use the new NetworkManager system.

## Basic Usage

### Getting the NetworkManager Instance

```java
// Get the manager for a specific world
World world = player.worldObj;
NetworkManager manager = NetworkManager.getInstance(world);
```

### Adding a New Member to the Network

When a new pipe or hatch is placed:

```java
@Override
public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
    super.onFirstTick(aBaseMetaTileEntity);
    if (aBaseMetaTileEntity.isServerSide()) {
        NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());
        manager.onMemberAdded(this);
    }
}
```

### Removing a Member from the Network

When a pipe or hatch is broken:

```java
@Override
public void onRemoval() {
    super.onRemoval();
    IGregTechTileEntity baseTile = getBaseMetaTileEntity();
    if (baseTile != null && baseTile.isServerSide()) {
        NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
        manager.onMemberRemoved(this);
    }
}
```

### Handling Connection Changes

When a wrench connects or disconnects cables:

```java
@Override
public boolean onWrenchRightClick(ForgeDirection side, ForgeDirection wrenchingSide,
                                   EntityPlayer aPlayer, float aX, float aY, float aZ,
                                   ItemStack aTool) {
    if (GTMod.proxy.gt6Pipe) {
        final ForgeDirection tSide = GTUtility.determineWrenchingSide(side, aX, aY, aZ);

        if (isConnectedAtSide(tSide)) {
            disconnect(tSide);
            GTUtility.sendChatToPlayer(aPlayer, "Disconnected");
        } else {
            if (connect(tSide) > 0) {
                GTUtility.sendChatToPlayer(aPlayer, "Connected");
            }
        }

        // Notify NetworkManager about connection change
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onConnectionChanged(this);
        }

        return true;
    }
    return false;
}
```

### Handling Block Updates

When a neighboring block changes:

```java
@Override
public void onMachineBlockUpdate() {
    IGregTechTileEntity baseTile = getBaseMetaTileEntity();
    if (baseTile != null && baseTile.isServerSide()) {
        NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
        manager.onConnectionChanged(this);
    }
}
```

## Advanced Usage

### Getting Network Information

```java
// Get the network for a specific member
IntegratedFluidNetwork network = member.getNetwork();

if (network != null) {
    // Get stored fluid
    FluidStack fluid = network.getStoredFluid();
    int amount = network.getStoredAmount();

    // Get capacity
    int capacity = network.getMaxCapacity();
    int available = network.getAvailableSpace();

    // Get network properties
    float pressure = network.getPressure();
    float temperature = network.getTemperature();
    int memberCount = network.getMemberCount();
}
```

### Adding Fluid to a Network

```java
// Add fluid through a hatch
public int addFluidToNetwork(FluidStack fluid, boolean simulate) {
    if (network == null) {
        // Try to join network if not connected
        NetworkManager manager = NetworkManager.getInstance(getBaseMetaTileEntity().getWorld());
        manager.onMemberAdded(this);
    }

    if (network != null) {
        // Add fluid with default temperature
        return network.addFluid(fluid, simulate);

        // Or add with specific temperature
        // return network.addFluid(fluid, simulate, temperatureInKelvin);
    }

    return 0;
}
```

### Draining Fluid from a Network

```java
// Drain fluid through a hatch
public FluidStack drainFluidFromNetwork(int maxDrain, boolean simulate) {
    if (network != null) {
        return network.drainFluid(maxDrain, simulate);
    }
    return null;
}

// Drain specific fluid
public FluidStack drainFluidFromNetwork(FluidStack fluid, boolean simulate) {
    if (network != null) {
        return network.drainFluid(fluid, simulate);
    }
    return null;
}
```

## Custom Network Member Implementation

If you're creating a new type of network member:

```java
public class MyCustomFluidDevice extends MetaTileEntity implements IIntegratedFluidMember {

    private IntegratedFluidNetwork network;

    @Override
    public IntegratedFluidNetwork getNetwork() {
        return network;
    }

    @Override
    public void setNetwork(IntegratedFluidNetwork network) {
        this.network = network;
    }

    @Override
    public void onNetworkUpdate() {
        // Called when network changes
        // Update UI, recalculate values, etc.
    }

    @Override
    public int getCapacityContribution() {
        // Return how much capacity this device adds to the network
        // Examples:
        // - Pipe: 100 mB
        // - Hatch: 10,000 mB
        // - Tank: 100,000 mB
        // - Injector: 0 mB (doesn't store fluid)
        return 10000; // 10 buckets
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());
            manager.onMemberAdded(this);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null && baseTile.isServerSide()) {
            NetworkManager manager = NetworkManager.getInstance(baseTile.getWorld());
            manager.onMemberRemoved(this);
        }
    }
}
```

## Debugging Tips

### Enable Debug Logging

You can modify NetworkManager to add debug output:

```java
// In NetworkManager.java
private static final boolean DEBUG = Boolean.getBoolean("integratedfluid.debug");

public void onMemberAdded(IIntegratedFluidMember member) {
    if (DEBUG) {
        System.out.println("[IntegratedFluid] Adding member: " + member);
    }
    // ... rest of implementation
}
```

Then run with: `-DintegratedFluid.debug=true`

### Check Network Consistency

```java
public void debugNetwork(IIntegratedFluidMember member) {
    IntegratedFluidNetwork network = member.getNetwork();
    if (network == null) {
        System.out.println("Member has no network!");
        return;
    }

    System.out.println("=== Network Debug Info ===");
    System.out.println("Member count: " + network.getMemberCount());
    System.out.println("Capacity: " + network.getMaxCapacity() + " mB");
    System.out.println("Stored: " + network.getStoredAmount() + " mB");
    System.out.println("Pressure: " + network.getPressure() + " bar");
    System.out.println("Temperature: " + network.getTemperature() + " K");

    if (network.getStoredFluid() != null) {
        System.out.println("Fluid: " + network.getStoredFluid().getLocalizedName());
    }

    System.out.println("Members:");
    for (IIntegratedFluidMember m : network.getMembers()) {
        System.out.println("  - " + m.getClass().getSimpleName() +
                          " (capacity: " + m.getCapacityContribution() + ")");
    }
}
```

### Visualize Network in WAILA

All pipes and hatches already show network info in WAILA:
- Fluid type and amount
- Network capacity
- Member count
- Pressure and temperature

## Common Patterns

### Pattern 1: Periodic Network Check

```java
@Override
public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
    super.onPostTick(aBaseMetaTileEntity, aTick);

    // Check every second (20 ticks)
    if (aBaseMetaTileEntity.isServerSide() && aTick % 20 == 0) {
        if (network == null) {
            // Try to rejoin network if disconnected
            NetworkManager manager = NetworkManager.getInstance(aBaseMetaTileEntity.getWorld());
            manager.onMemberAdded(this);
        }
    }
}
```

### Pattern 2: Conditional Fluid Transfer

```java
public boolean tryTransferFluid() {
    if (network == null || network.getStoredFluid() == null) {
        return false;
    }

    FluidStack available = network.getStoredFluid();
    if (available.amount < requiredAmount) {
        return false; // Not enough fluid
    }

    // Try to drain
    FluidStack drained = network.drainFluid(requiredAmount, false);
    if (drained != null && drained.amount == requiredAmount) {
        // Use the fluid
        processFluid(drained);
        return true;
    }

    return false;
}
```

### Pattern 3: Temperature-Aware Fluid Addition

```java
public void addHotFluid(FluidStack fluid, float temperature) {
    if (network != null) {
        // Add fluid with its temperature
        // Network will automatically calculate weighted average
        network.addFluid(fluid, false, temperature);

        // Notify members about temperature change
        for (IIntegratedFluidMember member : network.getMembers()) {
            member.onNetworkUpdate();
        }
    }
}
```

## Performance Considerations

### Do's ✅
- Use NetworkManager for all network operations
- Cache the NetworkManager instance if doing multiple operations
- Let NetworkManager handle flood-fill - don't implement your own
- Use `onNetworkUpdate()` to update cached values

### Don'ts ❌
- Don't call `rebuildNetwork()` manually (it's deprecated)
- Don't create your own flood-fill implementation
- Don't modify network membership directly
- Don't hold strong references to networks (use WeakReference if needed)

## Testing Your Integration

1. **Test Basic Connection**
   - Place your device
   - Verify it joins network (check WAILA)
   - Add fluid, verify it appears in network

2. **Test Network Merging**
   - Create two separate networks with your device
   - Connect them with a pipe
   - Verify fluids merge correctly

3. **Test Network Splitting**
   - Create connected network with your device
   - Break a pipe to split it
   - Verify fluid distributes proportionally

4. **Test Wrench Operations**
   - Use wrench to disconnect connections
   - Verify network splits correctly
   - Reconnect and verify merge

5. **Test Save/Load**
   - Save world with networks containing your device
   - Reload world
   - Verify networks restore correctly

## Need Help?

If you encounter issues:
1. Check the logs for errors
2. Use WAILA to inspect network state
3. Enable debug logging
4. Report issues on GitHub with:
   - Steps to reproduce
   - Expected vs actual behavior
   - Log files
   - Screenshots if relevant

## Further Reading

- `IntegratedFluidNetwork.java` - Core network class
- `NetworkManager.java` - Network management system
- `docs/IntegratedFluidNetwork_Rewrite.md` - Technical documentation
- `docs/IntegratedFluidNetwork_Summary.md` - Quick overview

