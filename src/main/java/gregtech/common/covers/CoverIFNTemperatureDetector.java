package gregtech.common.covers;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.metatileentity.implementations.integratedfluid.IFNAmbientTemperature;
import gregtech.api.metatileentity.implementations.integratedfluid.IntegratedFluidNetwork;

public final class CoverIFNTemperatureDetector extends CoverIFNDetectorBase {

    public CoverIFNTemperatureDetector(CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, 0.0d, 300.0d);
    }

    @Override
    protected double readValue(IntegratedFluidNetwork network) {
        return network.getTemperature();
    }

    @Override
    protected double readReferenceValue(ICoverable coverable, IntegratedFluidNetwork network) {
        return IFNAmbientTemperature.getAmbientTemperature(coverable == null ? null : coverable.getWorld());
    }
}
