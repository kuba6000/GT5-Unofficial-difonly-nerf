package gregtech.common.blocks;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import gregtech.common.tileentities.machines.multi.radiator.RadiatorModuleRegistry;

public class ItemRadiatorModule extends ItemStorage {

    public ItemRadiatorModule(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        super.addInformation(stack, player, lines, advanced);
        if (!(this.field_150939_a instanceof BlockRadiatorModule moduleBlock)) {
            return;
        }

        RadiatorModuleRegistry.ModuleData data = RadiatorModuleRegistry.getModuleData(moduleBlock, stack.getItemDamage());
        if (data == null) {
            return;
        }

        lines.add(String.format("Conductive coefficient: %.2f", data.conductiveCoefficient));
        if (data.type == RadiatorModuleRegistry.ModuleType.HEAT_EXCHANGE) {
            lines.add(String.format("Radiative coefficient: %.2e", data.radiativeCoefficient));
        }
    }
}
