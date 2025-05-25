package com.blockrotationlock.mixin.client;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.blockrotationlock.PlacementHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientWorld world;

    @Redirect(method = "doItemUse()V", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactBlock(" +
                    "Lnet/minecraft/client/network/ClientPlayerEntity;" +
                    "Lnet/minecraft/util/Hand;" +
                    "Lnet/minecraft/util/hit/BlockHitResult;" +
                    ")Lnet/minecraft/util/ActionResult;"))
    private ActionResult onProcessRightClickBlock(
            ClientPlayerInteractionManager controller,
            ClientPlayerEntity player,
            Hand hand,
            BlockHitResult hitResult)
    {
        return PlacementHandler.encodeProtocolValue(controller, player, this.world, hand, hitResult);
    }
}
