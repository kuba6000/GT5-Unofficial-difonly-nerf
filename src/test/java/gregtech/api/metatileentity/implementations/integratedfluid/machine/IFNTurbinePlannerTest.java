package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IFNTurbinePlannerTest {

    @Test
    void turbineExtractsEnergyFromPressureDropWhenBladeIsInstalled() {
        IFNTurbinePlanner.Blade blade = IFNTurbinePlanner.Blade.of(300.0f, 900.0f, 1.0f, 8.0f, 10_000);

        IFNTurbinePlanner.Plan plan = IFNTurbinePlanner.plan(IFNTurbinePlanner.Request.withBlade(
            8.0f,
            2.0f,
            500.0f,
            1000.0d,
            2.0d,
            blade,
            false));

        assertEquals(IFNTurbinePlanner.Status.READY, plan.status());
        assertEquals(1000.0d, plan.flowLitersPerTick(), 0.0001d);
        assertEquals(12_000L, plan.energyEu(), 0L);
        assertEquals(1, plan.bladeDamage());
    }

    @Test
    void noBladeBypassesGradientWithoutEnergyExtraction() {
        IFNTurbinePlanner.Plan plan = IFNTurbinePlanner.plan(IFNTurbinePlanner.Request.noBlade(
            8.0f,
            2.0f,
            1000.0d));

        assertEquals(IFNTurbinePlanner.Status.BYPASS, plan.status());
        assertEquals(1000.0d, plan.flowLitersPerTick(), 0.0001d);
        assertEquals(0L, plan.energyEu());
        assertEquals(0, plan.bladeDamage());
    }

    @Test
    void backpressureBlocksTurbineWhenOutputPressureIsNotLower() {
        IFNTurbinePlanner.Blade blade = IFNTurbinePlanner.Blade.of(300.0f, 900.0f, 1.0f, 8.0f, 10_000);

        IFNTurbinePlanner.Plan plan = IFNTurbinePlanner.plan(IFNTurbinePlanner.Request.withBlade(
            2.0f,
            2.0f,
            500.0f,
            1000.0d,
            2.0d,
            blade,
            false));

        assertEquals(IFNTurbinePlanner.Status.BACKPRESSURE_BLOCKED, plan.status());
        assertEquals(0.0d, plan.flowLitersPerTick(), 0.0001d);
    }

    @Test
    void twoPhaseExpansionAddsBladeDamagePenalty() {
        IFNTurbinePlanner.Blade blade = IFNTurbinePlanner.Blade.of(300.0f, 900.0f, 1.0f, 8.0f, 10_000);

        IFNTurbinePlanner.Plan plan = IFNTurbinePlanner.plan(IFNTurbinePlanner.Request.withBlade(
            8.0f,
            2.0f,
            500.0f,
            1000.0d,
            2.0d,
            blade,
            true));

        assertEquals(6, plan.bladeDamage());
    }
}
