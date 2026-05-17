package gregtech.api.metatileentity.implementations.integratedfluid.amount;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IFNAmountTypesTest {

    @Test
    void playerUnitFactoriesStoreFixedPointRawUnits() {
        EnergyAmount energy = EnergyAmount.fromEu(42);
        VolumeAmount volume = VolumeAmount.fromLiters(100);
        Pressure pressure = Pressure.fromBar(1.25d);
        Temperature temperature = Temperature.fromKelvin(300.5d);

        assertEquals(42, energy.toWholeEu());
        assertEquals(EnergyAmount.SCALE * 42L, energy.rawUnits());
        assertEquals(100, volume.toWholeLiters());
        assertEquals(VolumeAmount.SCALE * 100L, volume.rawUnits());
        assertEquals(1.25d, pressure.toBar(), 0.000001d);
        assertEquals(300.5d, temperature.toKelvin(), 0.000001d);
    }

    @Test
    void expectedNetworkScaleValuesDoNotOverflowFactories() {
        long largeNetworkSize = 1_000_000_000L;

        assertDoesNotThrow(() -> EnergyAmount.fromEu(largeNetworkSize));
        assertDoesNotThrow(() -> VolumeAmount.fromLiters(largeNetworkSize));
        assertDoesNotThrow(() -> SubstanceAmount.fromRefLiters(largeNetworkSize));
        assertDoesNotThrow(() -> Pressure.fromBar(1_000_000.0d));
        assertDoesNotThrow(() -> Temperature.fromKelvin(1_000_000.0d));
    }

    @Test
    void negativePlayerUnitFactoriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnergyAmount.fromEu(-1L));
        assertThrows(IllegalArgumentException.class, () -> VolumeAmount.fromLiters(-1L));
        assertThrows(IllegalArgumentException.class, () -> SubstanceAmount.fromRefLiters(-1L));
        assertThrows(IllegalArgumentException.class, () -> Pressure.fromBar(-1.0d));
        assertThrows(IllegalArgumentException.class, () -> Temperature.fromKelvin(-1.0d));
    }

    @Test
    void overflowingIntegerUnitFactoriesAreRejected() {
        assertThrows(ArithmeticException.class, () -> EnergyAmount.fromEu(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> VolumeAmount.fromLiters(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> SubstanceAmount.fromRefLiters(Long.MAX_VALUE));
    }
}
