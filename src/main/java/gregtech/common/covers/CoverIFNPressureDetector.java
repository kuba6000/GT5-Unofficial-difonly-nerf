package gregtech.common.covers;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class CoverIFNPressureDetector extends CoverIFNDetectorBase {

    public CoverIFNPressureDetector(CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, 0.0d, 1.0d);
    }

    @Override
    protected double readValue(IntegratedFluidNetwork network) {
        return network.getPressure();
    }

    @Override
    protected double readReferenceValue(ICoverable coverable, IntegratedFluidNetwork network) {
        return 1.0d;
    }
}
