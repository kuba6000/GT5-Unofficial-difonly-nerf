package gregtech.common.covers;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class CoverIFNTemperatureDetector extends CoverIFNDetectorBase {

    public CoverIFNTemperatureDetector(CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, 0.0d, 300.0d);
    }

    @Override
    protected double readValue(IntegratedFluidNetwork network) {
        return network.getTemperature();
    }
}
