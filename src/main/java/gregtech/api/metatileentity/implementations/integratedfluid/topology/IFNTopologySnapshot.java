package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;
import gregtech.api.metatileentity.implementations.integratedfluid.amount.VolumeAmount;

public final class IFNTopologySnapshot {

    private final int memberCount;
    private final VolumeAmount baseVolume;
    private final VolumeAmount accumulatorVolume;
    private final boolean incomplete;

    private IFNTopologySnapshot(int memberCount, VolumeAmount baseVolume, VolumeAmount accumulatorVolume,
        boolean incomplete) {
        this.memberCount = memberCount;
        this.baseVolume = baseVolume;
        this.accumulatorVolume = accumulatorVolume;
        this.incomplete = incomplete;
    }

    public static IFNTopologySnapshot fromMembers(Collection<? extends IIntegratedFluidMember> members,
        int expectedMemberCount) {
        long baseLiters = 0L;
        long accumulatorLiters = 0L;
        int count = 0;

        if (members != null) {
            for (IIntegratedFluidMember member : members) {
                if (member == null) {
                    continue;
                }
                count++;
                baseLiters = Math.addExact(baseLiters, Math.max(0, member.getCapacityContribution()));
                accumulatorLiters = Math.addExact(accumulatorLiters, Math.max(0, member.getAccumulatorContribution()));
            }
        }

        boolean incomplete = expectedMemberCount > 0 && count < expectedMemberCount;
        return new IFNTopologySnapshot(
            count,
            VolumeAmount.fromLiters(baseLiters),
            VolumeAmount.fromLiters(accumulatorLiters),
            incomplete
        );
    }

    public int memberCount() {
        return memberCount;
    }

    public VolumeAmount baseVolume() {
        return baseVolume;
    }

    public VolumeAmount accumulatorVolume() {
        return accumulatorVolume;
    }

    public VolumeAmount totalVolume() {
        return VolumeAmount.fromRawUnits(Math.addExact(baseVolume.rawUnits(), accumulatorVolume.rawUnits()));
    }

    public boolean incomplete() {
        return incomplete;
    }
}
