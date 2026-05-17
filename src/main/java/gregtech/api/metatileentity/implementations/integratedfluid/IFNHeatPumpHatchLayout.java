package gregtech.api.metatileentity.implementations.integratedfluid;

public final class IFNHeatPumpHatchLayout {

    public static final int RED = 1;
    public static final int BLUE = 4;

    private IFNHeatPumpHatchLayout() {}

    public static boolean isValidSplitFlowLayout(int inputCount, int[] outputColors) {
        if (inputCount != 1 || outputColors == null || outputColors.length != 2) {
            return false;
        }
        return hasExactlyOne(outputColors, RED) && hasExactlyOne(outputColors, BLUE);
    }

    public static boolean isValidHeatExchangerLayout(int[] inputColors, int[] outputColors) {
        return hasAtLeastOne(inputColors, RED)
            && hasAtLeastOne(inputColors, BLUE)
            && hasAtLeastOne(outputColors, RED)
            && hasAtLeastOne(outputColors, BLUE);
    }

    private static boolean hasExactlyOne(int[] colors, int targetColor) {
        return count(colors, targetColor) == 1;
    }

    private static boolean hasAtLeastOne(int[] colors, int targetColor) {
        return count(colors, targetColor) > 0;
    }

    private static int count(int[] colors, int targetColor) {
        if (colors == null) {
            return 0;
        }
        int count = 0;
        for (int color : colors) {
            if (color == targetColor) {
                count++;
            }
        }
        return count;
    }
}
