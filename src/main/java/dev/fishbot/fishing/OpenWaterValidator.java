package dev.fishbot.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Client-side replication of the vanilla 1.16+ "open water" check.
 *
 * <p>Treasure loot (enchanted books, saddles, bows, name tags...) is only
 * granted when the bobber floats in open water: a 5x4x5 volume around the
 * bobber where every 5x5 layer is either entirely "above water" blocks
 * (air / lily pads) or entirely "inside water" blocks (water sources with
 * no collision), with water below and air above.
 *
 * <p>Mirrors {@code FishingHook.calculateOpenWater} /
 * {@code getOpenWaterTypeForBlock} from the 26.2 vanilla sources.
 */
public final class OpenWaterValidator {

	private enum OpenWaterType {
		ABOVE_WATER,
		INSIDE_WATER,
		INVALID
	}

	private OpenWaterValidator() {
	}

	public static boolean isOpenWater(Level level, BlockPos bobberPos) {
		OpenWaterType previousLayer = OpenWaterType.INVALID;

		// Layers from one block below the bobber up to two blocks above it.
		for (int y = -1; y <= 2; y++) {
			OpenWaterType layer = typeForArea(level, bobberPos.offset(-2, y, -2), bobberPos.offset(2, y, 2));
			switch (layer) {
				case ABOVE_WATER -> {
					if (previousLayer == OpenWaterType.INVALID) {
						return false;
					}
				}
				case INSIDE_WATER -> {
					if (previousLayer == OpenWaterType.ABOVE_WATER) {
						return false;
					}
				}
				case INVALID -> {
					return false;
				}
			}
			previousLayer = layer;
		}

		return true;
	}

	private static OpenWaterType typeForArea(Level level, BlockPos from, BlockPos to) {
		return BlockPos.betweenClosedStream(from, to)
				.map(pos -> typeForBlock(level, pos))
				.reduce((a, b) -> a == b ? a : OpenWaterType.INVALID)
				.orElse(OpenWaterType.INVALID);
	}

	private static OpenWaterType typeForBlock(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && !state.is(Blocks.LILY_PAD)) {
			FluidState fluidState = state.getFluidState();
			return fluidState.is(FluidTags.WATER) && fluidState.isSource()
					&& state.getCollisionShape(level, pos).isEmpty()
					? OpenWaterType.INSIDE_WATER
					: OpenWaterType.INVALID;
		}
		return OpenWaterType.ABOVE_WATER;
	}
}
