package gregtech.api.metatileentity.implementations.integratedfluid.machine;

import java.util.Objects;

public final class IFNPort {

    private final String name;
    private final Direction direction;

    private IFNPort(String name, Direction direction) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Port name must not be blank");
        }
        this.name = name;
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public static IFNPort input(String name) {
        return new IFNPort(name, Direction.INPUT);
    }

    public static IFNPort output(String name) {
        return new IFNPort(name, Direction.OUTPUT);
    }

    public String name() {
        return name;
    }

    public Direction direction() {
        return direction;
    }

    public boolean isInput() {
        return direction == Direction.INPUT;
    }

    public boolean isOutput() {
        return direction == Direction.OUTPUT;
    }

    public enum Direction {
        INPUT,
        OUTPUT
    }
}
