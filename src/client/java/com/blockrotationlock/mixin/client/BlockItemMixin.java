package com.blockrotationlock.mixin.client;

import com.blockrotationlock.BlockRotationLockModClient;
import com.blockrotationlock.PlacementPacket;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin extends Item {
    public BlockItemMixin(Settings settings) {
        super(settings);
    }

    @Shadow protected abstract boolean canPlace(ItemPlacementContext context, BlockState state);
    @Shadow public abstract Block getBlock();

    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
    private void blockrotationlock$modifyPlacementState(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (BlockRotationLockModClient.isRotationLockActive()) {
            BlockState original = this.getBlock().getPlacementState(ctx);

            if (original != null && this.canPlace(ctx, original)) {
                Direction locked = Direction.byName(BlockRotationLockModClient.getLockedDirection());
                Boolean lockedTopHalf = BlockRotationLockModClient.getLockedTopHalf();

                BlockState state = original;
                Block block = state.getBlock();
                BlockPos pos = ctx.getBlockPos();

                int protocolValue = (int) (ctx.getHitPos().x - pos.getX()) - 2;
                int bitOffset = 0;

                // Facing (dispensers, observers, etc)
                if (state.contains(Properties.FACING)) {
                    state = state.with(Properties.FACING, locked);

                    protocolValue |= (locked.getId() & 0x7) << bitOffset;
                    bitOffset += 3;
                }
                // Horizontal facing (furnaces, pumpkins, etc)
                else if (state.contains(Properties.HORIZONTAL_FACING) && locked.getAxis().isHorizontal()) {
                    if (block instanceof StairsBlock) {
                        // Use inverted direction for stairs
                        state = state.with(Properties.HORIZONTAL_FACING, locked.getOpposite());

                        protocolValue |= (locked.getOpposite().getId() & 0x3) << bitOffset;
                        bitOffset += 2; // Only 4 values
                    } else {
                        state = state.with(Properties.HORIZONTAL_FACING, locked);

                        protocolValue |= (locked.getId() & 0x3) << bitOffset;
                        bitOffset += 2; // Only 4 values
                    }
                }
                else {
                    bitOffset += 3; // Skip 3 bits if no facing property
                }
                bitOffset += 1; // Always skip the 4th bit 
                // Axis (logs, pillars, etc)
                if (state.contains(Properties.AXIS)) {
                    state = state.with(Properties.AXIS, locked.getAxis());

                    int axisIndex = switch (locked.getAxis()) {
                        case X -> 0;
                        case Y -> 1;
                        case Z -> 2;
                        default -> 0;
                    };
                    protocolValue |= (axisIndex & 0x3) << bitOffset;
                    bitOffset += 2;
                }
                // Block half (stairs, etc)
                if (state.contains(Properties.BLOCK_HALF) && lockedTopHalf != null) {
                    state = state.with(Properties.BLOCK_HALF, lockedTopHalf ? BlockHalf.TOP : BlockHalf.BOTTOM);

                    int halfIndex = lockedTopHalf ? 1 : 0;
                    protocolValue |= (halfIndex & 0x1) << bitOffset;
                    bitOffset += 1;
                }
                // Slab type
                if (state.contains(Properties.SLAB_TYPE) && lockedTopHalf != null) {
                    // Only set to TOP or BOTTOM, not DOUBLE
                    state = state.with(Properties.SLAB_TYPE, lockedTopHalf ? SlabType.TOP : SlabType.BOTTOM);

                    int slabIndex = lockedTopHalf ? 1 : 0;
                    protocolValue |= (slabIndex & 0x3) << bitOffset;
                    bitOffset += 2;
                }

                // Only override if placement is legal
                if (state.canPlaceAt(ctx.getWorld(), pos)) {
                    if (!BlockRotationLockModClient.hasIntegratedServer()) {

                        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

                        buf.writeBlockPos(ctx.getBlockPos());
                        buf.writeVarInt(protocolValue);
                        /*
                        buf.writeBlockPos(ctx.getBlockPos());
                        buf.writeVarInt(locked.getId()); // Make sure 'locked' is the correct Direction
                        buf.writeFloat((float) (ctx.getHitPos().x - ctx.getBlockPos().getX()));
                        buf.writeFloat((float) (ctx.getHitPos().y - ctx.getBlockPos().getY()));
                        buf.writeFloat((float) (ctx.getHitPos().z - ctx.getBlockPos().getZ()));
                        buf.writeVarInt(ctx.getHand() == Hand.MAIN_HAND ? 0 : 1);
                        buf.writeBoolean(ctx.getPlayer() != null && ctx.getPlayer().isSneaking());
                        */

                        ClientPlayNetworking.send(new PlacementPacket.Payload(buf));
                    }

                    cir.setReturnValue(state);
                }
            }
        }
    }
}