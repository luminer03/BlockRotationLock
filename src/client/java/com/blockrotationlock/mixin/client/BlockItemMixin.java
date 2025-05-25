package com.blockrotationlock.mixin.client;

import com.blockrotationlock.BlockRotationLockModClient;
import com.blockrotationlock.PlacementHandler;
import com.blockrotationlock.PlacementHandler.UseContext;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;

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
        BlockState stateOrig = this.getBlock().getPlacementState(ctx);
        if(BlockRotationLockModClient.isRotationLockActive())
        {
            if (stateOrig != null && this.canPlace(ctx, stateOrig))
            {
                UseContext context = UseContext.from(ctx, ctx.getHand());
                cir.setReturnValue(PlacementHandler.decodeProtocolValue(stateOrig, context));
            }
        }
    }
}