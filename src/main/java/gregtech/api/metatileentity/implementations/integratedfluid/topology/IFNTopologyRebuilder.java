package gregtech.api.metatileentity.implementations.integratedfluid.topology;

import java.util.Collection;

import gregtech.api.metatileentity.implementations.integratedfluid.IIntegratedFluidMember;

public final class IFNTopologyRebuilder {

    private IFNTopologyRebuilder() {}

    public static Result rebuild(Collection<? extends IIntegratedFluidMember> members, int expectedMemberCount) {
        int observedMemberCount = countMembers(members);
        int effectiveExpectedCount = expectedMemberCount <= 0
            ? observedMemberCount
            : Math.max(expectedMemberCount, observedMemberCount);
        IFNTopologySnapshot snapshot = IFNTopologySnapshot.fromMembers(members, effectiveExpectedCount);
        return new Result(snapshot, effectiveExpectedCount, snapshot.incomplete());
    }

    private static int countMembers(Collection<? extends IIntegratedFluidMember> members) {
        int count = 0;
        if (members == null) {
            return 0;
        }
        for (IIntegratedFluidMember member : members) {
            if (member != null) {
                count++;
            }
        }
        return count;
    }

    public static final class Result {

        private final IFNTopologySnapshot snapshot;
        private final int expectedMemberCount;
        private final boolean pending;

        private Result(IFNTopologySnapshot snapshot, int expectedMemberCount, boolean pending) {
            this.snapshot = snapshot;
            this.expectedMemberCount = expectedMemberCount;
            this.pending = pending;
        }

        public IFNTopologySnapshot snapshot() {
            return snapshot;
        }

        public int expectedMemberCount() {
            return expectedMemberCount;
        }

        public boolean pending() {
            return pending;
        }
    }
}
