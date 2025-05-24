package com.blockrotationlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.BlockState;

public class PlacementHandler {
    public static final ImmutableSet<Property<?>> WHITELISTED_PROPERTIES = ImmutableSet.of(
        Properties.INVERTED,
        Properties.OPEN,
        Properties.ATTACHMENT,
        Properties.AXIS,
        Properties.BLOCK_HALF,
        Properties.BLOCK_FACE,
        Properties.CHEST_TYPE,
        Properties.COMPARATOR_MODE,
        Properties.DOOR_HINGE,
        Properties.FACING,
        Properties.HOPPER_FACING,
        Properties.HORIZONTAL_FACING,
        Properties.ORIENTATION,
        Properties.RAIL_SHAPE,
        Properties.STRAIGHT_RAIL_SHAPE,
        Properties.SLAB_TYPE,
        Properties.STAIR_SHAPE,
        Properties.BITES,
        Properties.DELAY,
        Properties.NOTE,
        Properties.ROTATION
    );
    public static final ImmutableSet<Property<?>> BLACKLISTED_PROPERTIES = ImmutableSet.of(
        Properties.WATERLOGGED,
        Properties.POWERED
    );

    public static void sendPlacementPacket(BlockState state, BlockPos pos) {
        int protocolValue = 0;
        int bitOffset = 0;

        // Direction property (handled first, always 3 bits + 1 unused)
        Optional<Property<?>> directionProp = state.getProperties().stream()
            .filter(p -> p == Properties.FACING || p == Properties.HORIZONTAL_FACING)
            .findFirst();

        if (directionProp.isPresent()) {
            // Already handled in your code, so skip here
            bitOffset += 3;
        }
        bitOffset += 1; // Always skip 1 unused bit

        List<Property<?>> propList = new ArrayList<>(state.getProperties());
        propList.sort(Comparator.comparing(Property::getName));

        for (Property<?> prop : propList) {
            if (directionProp.isPresent() && directionProp.get().equals(prop)) continue;
            if (!PlacementHandler.WHITELISTED_PROPERTIES.contains(prop)) continue;
            if (PlacementHandler.BLACKLISTED_PROPERTIES.contains(prop)) continue;

            List<?> values = new ArrayList<>(prop.getValues());
            Collections.sort((List)values);

            int requiredBits = MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(values.size()));
            int valueIndex = values.indexOf(lockedState.get(prop));
            protocolValue |= (valueIndex & ((1 << requiredBits) - 1)) << bitOffset;
            bitOffset += requiredBits;
        }
    }
}
