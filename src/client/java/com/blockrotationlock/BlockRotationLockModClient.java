package com.blockrotationlock;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BlockRotationLockModClient implements ClientModInitializer {
    private static KeyBinding toggleKeyBinding;
    private static boolean rotationLockActive = false;
    private static String lockedDirection = null;
	private static Boolean lockedTopHalf = null; // true = top half, false = bottom half, null = not applicable
    private static final Identifier INDICATOR_LAYER = Identifier.of("blockrotationlock", "indicator-layer");

    @Override
    public void onInitializeClient() {
        // Register the keybinding (default: H key)
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockrotationlock.toggle", // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H, // Default key: H
                "category.blockrotationlock.main" // category
        ));

        HudLayerRegistrationCallback.EVENT.register(
            layeredDrawer -> layeredDrawer.attachLayerAfter(
                IdentifiedLayer.CROSSHAIR, INDICATOR_LAYER, BlockRotationLockModClient::renderIndicator
            )
        );

        // Listen for key press each client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKeyBinding.wasPressed()) {
                if (client.player != null) {
                    // Determine current direction and topHalf
                    String currentDirection;
                    Boolean currentTopHalf = null;
                    if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
                        Direction face = hit.getSide();
                        currentDirection = face.getName();
                        if (face.getAxis().isHorizontal()) {
                            double yOnBlock = hit.getPos().y - hit.getBlockPos().getY();
                            currentTopHalf = yOnBlock > 0.5;
                        }
                    } else {
                        Vec3d look = client.player.getRotationVecClient();
                        Direction dir = Direction.getFacing(look.x, look.y, look.z);
                        currentDirection = dir.getOpposite().getName(); // Reverse the direction
                    }

                    // If lock is inactive, activate it
                    if (!rotationLockActive) {
                        lockedDirection = currentDirection;
                        lockedTopHalf = currentTopHalf;
                        rotationLockActive = true;
                    } else {
                        // If lock is active, check if direction or topHalf changed
                        boolean sameDirection = lockedDirection != null && lockedDirection.equals(currentDirection);
                        boolean sameTopHalf = (lockedTopHalf == null && currentTopHalf == null)
                                || (lockedTopHalf != null && lockedTopHalf.equals(currentTopHalf));
                        if (!sameDirection || !sameTopHalf) {
                            // Update lock to new direction/half
                            lockedDirection = currentDirection;
                            lockedTopHalf = currentTopHalf;
                        } else {
                            // Disable lock
                            rotationLockActive = false;
                            lockedDirection = null;
                            lockedTopHalf = null;
                        }
                    }
                }
            }
        });
    }

    private static void renderIndicator(DrawContext context, RenderTickCounter tickCounter) {
        if (rotationLockActive) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.options.getPerspective().isFirstPerson() && client.currentScreen == null) {
                int iconIndex = 0;
                if (lockedDirection != null) {
                    switch (lockedDirection) {
                        case "up": iconIndex = 0; break;
                        case "down": iconIndex = 1; break;
                        case "north": iconIndex = 2; break;
                        case "south": iconIndex = 3; break;
                        case "west": iconIndex = 4; break;
                        case "east": iconIndex = 5; break;
                        default: iconIndex = 0;
                    }
                }
                int iconSize = 16;
                int textureSize = 64;
                int iconsPerRow = textureSize / iconSize;
                int u = (iconIndex % iconsPerRow) * iconSize;
                int v = (iconIndex / iconsPerRow) * iconSize;

                int screenWidth = client.getWindow().getScaledWidth();
                int screenHeight = client.getWindow().getScaledHeight();

                int x = screenWidth / 2 + 8;
                int y = screenHeight / 2 - 8;

                Identifier texture = Identifier.of("blockrotationlock", "indicator.png");

                // Draw main direction icon
                context.drawTexture(RenderLayer::getCrosshair, texture, x, y, u, v, iconSize, iconSize, textureSize, textureSize);

                // Draw top/bottom icon if applicable
                if (lockedTopHalf != null) {
                    int halfIconIndex = lockedTopHalf ? 9 : 8;
                    int halfU = (halfIconIndex % iconsPerRow) * iconSize;
                    int halfV = (halfIconIndex / iconsPerRow) * iconSize;
                    // Draw the half icon slightly below the main icon
                    context.drawTexture(RenderLayer::getCrosshair, texture, x + iconSize, y, halfU, halfV, iconSize, iconSize, textureSize, textureSize);
                }
            }
        }
    }

	public static boolean isRotationLockActive() {
        return rotationLockActive;
    }

    public static String getLockedDirection() {
        return lockedDirection;
    }

	public static Boolean getLockedTopHalf() {
        return lockedTopHalf;
    }
}