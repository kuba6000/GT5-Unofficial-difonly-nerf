package gregtech.api.metatileentity.implementations.integratedfluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import gregtech.api.metatileentity.BaseMetaPipeEntity;

class MTEIntegratedFluidPipeLifecycleTest {

    @Test
    void chunkUnloadDoesNotRemoveIntegratedFluidPipeFromNetwork() {
        BaseMetaPipeEntity baseTile = new BaseMetaPipeEntity();
        TrackingPipe pipe = attachPipe(baseTile);

        baseTile.onChunkUnload();

        assertEquals(0, pipe.removalCalls);
        assertSame(baseTile, pipe.getBaseMetaTileEntity());
    }

    @Test
    void invalidationRemovesIntegratedFluidPipeFromNetwork() {
        BaseMetaPipeEntity baseTile = new BaseMetaPipeEntity();
        TrackingPipe pipe = attachPipe(baseTile);

        baseTile.invalidate();

        assertEquals(1, pipe.removalCalls);
        assertNull(pipe.getBaseMetaTileEntity());
    }

    private static TrackingPipe attachPipe(BaseMetaPipeEntity baseTile) {
        TrackingPipe pipe = new TrackingPipe();
        baseTile.setMetaTileEntity(pipe);
        pipe.setBaseMetaTileEntity(baseTile);
        return pipe;
    }

    private static final class TrackingPipe extends MTEIntegratedFluidPipe {

        private int removalCalls;

        private TrackingPipe() {
            super("ifn_lifecycle_test_pipe");
        }

        @Override
        public void onRemoval() {
            removalCalls++;
        }
    }
}
