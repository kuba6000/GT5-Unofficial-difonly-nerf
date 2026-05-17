package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.Test;

class MTEIntegratedFluidExtractorTest {

    @Test
    void extractorOnlyDrainsNormalNetworks() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(liquid, 1_000, 0, 1.0f);

        assertTrue(MTEIntegratedFluidExtractor.canExtractFromNetwork(network));
        assertFalse(MTEIntegratedFluidExtractor.canExtractFromNetwork(null));

        network.setPending(true);

        assertFalse(MTEIntegratedFluidExtractor.canExtractFromNetwork(network));

        network.setPending(false);
        network.freeze("test freeze");

        assertFalse(MTEIntegratedFluidExtractor.canExtractFromNetwork(network));
    }

    @Test
    void frozenNetworkDrainReturnsNullWithoutChangingState() {
        Fluid liquid = IFNTestSupport.liquidFluid();
        IntegratedFluidNetwork network = IFNTestSupport.newNetwork(liquid, 1_000, 0, 1.0f);
        MTEIntegratedFluidExtractor extractor = new MTEIntegratedFluidExtractor("extractor", 0, new String[0], null);

        extractor.setNetwork(network);
        network.freeze("test freeze");

        long beforeAmountQ = network.getAmountQ();

        assertNull(extractor.drain(100, true));
        assertEquals(beforeAmountQ, network.getAmountQ());
    }
}
