package gregtech.common.covers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;
import gregtech.api.metatileentity.implementations.integratedfluid.MTEIntegratedFluidPipe;

class CoverIFNDetectorBaseTest {

    @Test
    void detectorCoverOnIntegratedFluidPipeUsesPipeNetwork() {
        IntegratedFluidNetwork network = new IntegratedFluidNetwork();
        network.setPressure(5.0F);

        BaseMetaPipeEntity pipeTile = new BaseMetaPipeEntity();
        MTEIntegratedFluidPipe pipe = new MTEIntegratedFluidPipe("ifn_detector_test_pipe");
        pipeTile.setMetaTileEntity(pipe);
        pipe.setBaseMetaTileEntity(pipeTile);
        pipe.setNetwork(network);

        TestDetectorCover cover = new TestDetectorCover(pipeTile);

        assertEquals(15, cover.signal(pipeTile));
    }

    private static final class TestDetectorCover extends CoverIFNDetectorBase {

        private TestDetectorCover(ICoverable coverable) {
            super(new CoverContext(null, ForgeDirection.UNKNOWN, coverable), null, 1.0d, 10.0d);
        }

        private byte signal(ICoverable coverable) {
            return computeSignal(coverable);
        }

        @Override
        protected double readValue(IntegratedFluidNetwork network) {
            return network.getPressure();
        }
    }
}
