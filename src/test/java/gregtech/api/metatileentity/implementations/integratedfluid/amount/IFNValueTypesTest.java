package gregtech.api.metatileentity.implementations.integratedfluid.amount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IFNValueTypesTest {

    @Test
    void energyAmountStoresEuAsFixedPoint() {
        EnergyAmount energy = EnergyAmount.fromEu(42);

        assertEquals(42, energy.toWholeEu());
        assertEquals(EnergyAmount.SCALE * 42L, energy.rawUnits());
    }

    @Test
    void volumeAmountStoresLitersAsFixedPoint() {
        VolumeAmount volume = VolumeAmount.fromLiters(100);

        assertEquals(100, volume.toWholeLiters());
        assertEquals(VolumeAmount.SCALE * 100L, volume.rawUnits());
    }

    @Test
    void pressureStoresBarsAsFixedPoint() {
        Pressure pressure = Pressure.fromBar(1.25d);

        assertEquals(1.25d, pressure.toBar(), 0.000001d);
    }

    @Test
    void temperatureStoresKelvinAsFixedPoint() {
        Temperature temperature = Temperature.fromKelvin(300.5d);

        assertEquals(300.5d, temperature.toKelvin(), 0.000001d);
    }
}
