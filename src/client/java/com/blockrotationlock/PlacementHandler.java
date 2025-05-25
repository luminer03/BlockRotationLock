package com.blockrotationlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;

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

    public static <T extends Comparable<T>> ActionResult encodeProtocolValue(
            ClientPlayerInteractionManager controller,
            ClientPlayerEntity player,
            ClientWorld world,
            Hand hand,
            BlockHitResult hitResult)
    {
        Item item = player.getStackInHand(hand).getItem();
        if (!(item instanceof BlockItem) && BlockRotationLockModClient.isRotationLockActive()) {
            return ActionResult.PASS;
        }

        BlockPos posIn = hitResult.getBlockPos();
        Direction sideIn = hitResult.getSide();
        Vec3d hitVec = hitResult.getPos();

        ItemPlacementContext ctx = new ItemPlacementContext(new ItemUsageContext(player, hand, hitResult));

        Block block = ((BlockItem) item).getBlock();
        BlockState state = block.getPlacementState(ctx);

        Direction locked = Direction.byName(BlockRotationLockModClient.getLockedDirection());
        Boolean lockedTopHalf = BlockRotationLockModClient.getLockedTopHalf();

        // Facing (dispensers, observers, etc)
        if (state.contains(Properties.FACING)) {
            state = state.with(Properties.FACING, locked);
        }
        // Horizontal facing (furnaces, pumpkins, etc)
        else if (state.contains(Properties.HORIZONTAL_FACING) && locked.getAxis().isHorizontal()) {
            if (block instanceof StairsBlock) {
                // Use inverted direction for stairs
                state = state.with(Properties.HORIZONTAL_FACING, locked.getOpposite());
            } else {
                state = state.with(Properties.HORIZONTAL_FACING, locked);
            }
        }
        // Axis (logs, pillars, etc)
        if (state.contains(Properties.AXIS)) {
            state = state.with(Properties.AXIS, locked.getAxis());
        }
        // Block half (stairs, etc)
        if (state.contains(Properties.BLOCK_HALF) && lockedTopHalf != null) {
            state = state.with(Properties.BLOCK_HALF, lockedTopHalf ? BlockHalf.TOP : BlockHalf.BOTTOM);
        }
        // Slab type
        if (state.contains(Properties.SLAB_TYPE) && lockedTopHalf != null) {
            // Only set to TOP or BOTTOM, not DOUBLE
            state = state.with(Properties.SLAB_TYPE, lockedTopHalf ? SlabType.TOP : SlabType.BOTTOM);
        }

        int protocolValue = 0;
        int bitOffset = 0;

        // Encode direction property if present and not VERTICAL_DIRECTION
        Optional<EnumProperty<Direction>> property = getFirstDirectionProperty(state);
        if (property.isPresent() && property.get() != Properties.VERTICAL_DIRECTION) {
            EnumProperty<Direction> dirProp = property.get();
            Direction dir = state.get(dirProp);
            int dirIndex = dir.getId(); // 0-5 for Direction, 6 for opposite, see decode
            protocolValue |= (dirIndex << bitOffset);
            bitOffset += 3; // 3 bits for direction
        }
        bitOffset += 1; // 1 bit consumed as in decode

        // Encode whitelisted properties (excluding the direction property)
        List<Property<?>> propList = new ArrayList<>(state.getBlock().getStateManager().getProperties());
        propList.sort(Comparator.comparing(Property::getName));

        for (Property<?> p : propList) {
            if ((property.isPresent() && !property.get().equals(p)) ||
                (property.isEmpty() && WHITELISTED_PROPERTIES.contains(p))) {
                @SuppressWarnings("unchecked")
                Property<T> prop = (Property<T>) p;
                List<T> list = new ArrayList<>(prop.getValues());
                list.sort(Comparable::compareTo);

                T value = state.get(prop);
                int valueIndex = list.indexOf(value);

                int requiredBits = MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(list.size()));
                protocolValue |= (valueIndex << bitOffset);
                bitOffset += requiredBits;
            }
        }

        double encodedX = posIn.getX() + 2 + protocolValue;
        Vec3d encodedHitVec = new Vec3d(encodedX, hitVec.y, hitVec.z);

        BlockHitResult encodedHitResult = new BlockHitResult(encodedHitVec, sideIn, posIn, false);
        ActionResult result = controller.interactBlock(player, hand, encodedHitResult);

        return result;
    }

    public static <T extends Comparable<T>> BlockState decodeProtocolValue(BlockState state, UseContext context)
    {
        int protocolValue = (int) (context.getHitVec().x - (double) context.getPos().getX()) - 2;
        BlockState oldState = state;

        if (protocolValue < 0)
        {
            return oldState;
        }

        Optional<EnumProperty<Direction>> property = getFirstDirectionProperty(state);

        // DirectionProperty - allow all except: VERTICAL_DIRECTION (PointedDripstone)
        if (property.isPresent() && property.get() != Properties.VERTICAL_DIRECTION)
        {
            state = applyDirectionProperty(state, context, property.get(), protocolValue);

            if (state == null)
            {
                return null;
            }

            if (state.canPlaceAt(context.getWorld(), context.getPos()))
            {
                oldState = state;
            }
            else
            {
                state = oldState;
            }
            // Consume the bits used for the facing
            protocolValue >>>= 3;
        }
        // Consume the lowest unused bit
        protocolValue >>>= 1;

        List<Property<?>> propList = new ArrayList<>(state.getBlock().getStateManager().getProperties());
        propList.sort(Comparator.comparing(Property::getName));

        for (Property<?> p : propList)
        {
            if ((property.isPresent() && !property.get().equals(p)) ||
                (property.isEmpty()) &&
                WHITELISTED_PROPERTIES.contains(p))
            {
                @SuppressWarnings("unchecked")
                Property<T> prop = (Property<T>) p;
                List<T> list = new ArrayList<>(prop.getValues());
                list.sort(Comparable::compareTo);

                int requiredBits = MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(list.size()));
                int bitMask = ~(0xFFFFFFFF << requiredBits);
                int valueIndex = protocolValue & bitMask;

                if (valueIndex >= 0 && valueIndex < list.size())
                {
                    T value = list.get(valueIndex);

                    if (state.get(prop).equals(value) == false &&
                        value != SlabType.DOUBLE) // don't allow duping slabs by forcing a double slab via the protocol
                    {
                        state = state.with(prop, value);

                        if (state.canPlaceAt(context.getWorld(), context.getPos()))
                        {
                            oldState = state;
                        }
                        else
                        {
                            state = oldState;
                        }
                    }

                    protocolValue >>>= requiredBits;
                }
            }
        }

        // Strip Blacklisted properties, and use the Block's default state.
        // This needs to be done after the initial loop, or it breaks compatibility
        for (Property<?> p : BLACKLISTED_PROPERTIES)
        {
            if (state.contains(p))
            {
                @SuppressWarnings("unchecked")
                Property<T> prop = (Property<T>) p;
                BlockState def = state.getBlock().getDefaultState();
                state = state.with(prop, def.get(prop));
            }
        }

        if (state.canPlaceAt(context.getWorld(), context.getPos()))
        {
            return state;
        }
        else
        {
            return null;
        }
    }

    private static BlockState applyDirectionProperty(BlockState state, UseContext context,
                                                     EnumProperty<Direction> property, int protocolValue)
    {
        Direction facingOrig = state.get(property);
        Direction facing = facingOrig;
        int decodedFacingIndex = (protocolValue & 0xF) >> 1;

        if (decodedFacingIndex == 6) // the opposite of the normal facing requested
        {
            facing = facing.getOpposite();
        }
        else if (decodedFacingIndex >= 0 && decodedFacingIndex <= 5)
        {
            facing = Direction.byId(decodedFacingIndex);

            if (property.getValues().contains(facing) == false)
            {
                facing = context.getEntity().getHorizontalFacing().getOpposite();
            }
        }

        if (facing != facingOrig && property.getValues().contains(facing))
        {
            if (state.getBlock() instanceof BedBlock)
            {
                BlockPos headPos = context.pos.offset(facing);
                ItemPlacementContext ctx = context.getItemPlacementContext();

                if (context.getWorld().getBlockState(headPos).canReplace(ctx) == false)
                {
                    return null;
                }
            }

            state = state.with(property, facing);
        }

        return state;
    }

    public static class UseContext
    {
        private final World world;
        private final BlockPos pos;
        private final Direction side;
        private final Vec3d hitVec;
        private final LivingEntity entity;
        private final Hand hand;
        @Nullable private final ItemPlacementContext itemPlacementContext;

        private UseContext(World world, BlockPos pos, Direction side, Vec3d hitVec,
                           LivingEntity entity, Hand hand, @Nullable ItemPlacementContext itemPlacementContext)
        {
            this.world = world;
            this.pos = pos;
            this.side = side;
            this.hitVec = hitVec;
            this.entity = entity;
            this.hand = hand;
            this.itemPlacementContext = itemPlacementContext;
        }

        public static UseContext from(ItemPlacementContext ctx, Hand hand)
        {
            Vec3d pos = ctx.getHitPos();
            return new UseContext(ctx.getWorld(), ctx.getBlockPos(), ctx.getSide(), new Vec3d(pos.x, pos.y, pos.z),
                                  ctx.getPlayer(), hand, ctx);
        }

        public World getWorld()
        {
            return this.world;
        }

        public BlockPos getPos()
        {
            return this.pos;
        }

        public Direction getSide()
        {
            return this.side;
        }

        public Vec3d getHitVec()
        {
            return this.hitVec;
        }

        public LivingEntity getEntity()
        {
            return this.entity;
        }

        public Hand getHand()
        {
            return this.hand;
        }

        @Nullable
        public ItemPlacementContext getItemPlacementContext()
        {
            return this.itemPlacementContext;
        }
    }

    @SuppressWarnings("unchecked")
    public static Optional<EnumProperty<Direction>> getFirstDirectionProperty(BlockState state)
    {
        for (Property<?> prop : state.getProperties())
        {
            if (prop instanceof EnumProperty<?> ep && ep.getType().equals(Direction.class))
            {
                return Optional.of((EnumProperty<Direction>) ep);
            }
        }

        return Optional.empty();
    }
}
