package me.alpha432.oyvey.features.modules.client;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.Render2DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudFeature extends Module {
    private enum Sorting { Length, Alphabetical }
    private enum ColorMode { Default, Wave, Transition }
    private enum HealthBarMode { None, Sync, Dynamic }

    private final Setting<Boolean> watermark = bool("Watermark", true);
    private final Setting<Boolean> moduleList = bool("ModuleList", true);
    private final Setting<Sorting> sorting = mode("Sorting", Sorting.Length);

    private final Setting<Boolean> coordinates = bool("Coordinates", true);
    private final Setting<Boolean> direction = bool("Direction", true);
    private final Setting<Boolean> nether = bool("Nether", true);

    private final Setting<Boolean> armor = bool("Armor", true);
    private final Setting<Double> armorScale = num("Armor Scale", 0.7d, 0.3d, 1.0d);
    private final Setting<Boolean> armorWarning = bool("Armor Warning", true);
    private final Setting<Integer> durabilityThreshold = num("Durability Threshold", 20, 1, 100);

    private final Setting<Boolean> potions = bool("Potions", true);
    private final Setting<Boolean> fps = bool("FPS", true);
    private final Setting<Boolean> tps = bool("TPS", true);
    private final Setting<Boolean> averageTps = bool("Average TPS", false);
    private final Setting<Boolean> ping = bool("Ping", true);
    private final Setting<Boolean> speedometer = bool("Speedometer", true);
    private final Setting<Boolean> brand = bool("Brand", true);
    private final Setting<Boolean> durability = bool("Durability", true);
    private final Setting<Boolean> pearlCooldown = bool("Pearl Cooldown", true);
    private final Setting<Boolean> serverLag = bool("Server Lag", true);
    private final Setting<Boolean> chestCounter = bool("Chest Counter", true);

    private final Setting<HealthBarMode> healthBar = mode("Health Bar", HealthBarMode.Sync);
    private final Setting<ColorMode> colorMode = mode("Color Mode", ColorMode.Default);
    private final Setting<Integer> secondColor = num("Second Color", 0xFFFFFF, 0, 0xFFFFFF);

    private final List<String> moduleEntries = new ArrayList<>();
    private float healthAnimation = 1.0f;
    private long lastChestCountMs;
    private int loadedChests;

    public HudFeature() {
        super("Hud", "Mint-inspired HUD with module list, player information, armor, potions and status indicators.", Category.CLIENT, false, false, false);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        DrawContext context = event.getContext();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        if (watermark.getValue()) {
            drawText(context, OyVey.NAME + " " + OyVey.VERSION, 2, 2, getHudColor(2));
        }

        if (moduleList.getValue()) {
            renderModuleList(context, width);
        }

        if (armor.getValue()) {
            renderArmor(context, width, height);
        }

        int infoOffset = 0;

        if (potions.getValue()) {
            infoOffset = renderPotions(context, width, height, infoOffset);
        }

        if (fps.getValue()) {
            infoOffset = renderInfoLine(context, width, height, infoOffset, "FPS", Integer.toString(MinecraftClient.getInstance().getCurrentFps()));
        }

        if (ping.getValue()) {
            infoOffset = renderInfoLine(context, width, height, infoOffset, "Ping", OyVey.serverManager.getPing() + "ms");
        }

        if (tps.getValue()) {
            String tpsText = String.format("%.2f", OyVey.serverManager.getTPS());
            if (averageTps.getValue()) {
                tpsText += " [" + String.format("%.2f", OyVey.serverManager.getTPS()) + "]";
            }
            infoOffset = renderInfoLine(context, width, height, infoOffset, "TPS", tpsText);
        }

        if (speedometer.getValue()) {
            infoOffset = renderInfoLine(context, width, height, infoOffset, "Speed",
                    new DecimalFormat("0.0").format(OyVey.speedManager.getSpeedKpH()) + "km/h");
        }

        if (brand.getValue() && !OyVey.serverManager.getServerBrand().isEmpty()) {
            infoOffset = renderInfoLine(context, width, height, infoOffset, "ServerBrand", OyVey.serverManager.getServerBrand());
        }

        if (durability.getValue() && mc.player.getMainHandStack().isDamageable()) {
            ItemStack stack = mc.player.getMainHandStack();
            int remaining = stack.getMaxDamage() - stack.getDamage();
            infoOffset = renderInfoLine(context, width, height, infoOffset, "Durability",
                    (remaining * 100 / stack.getMaxDamage()) + "%");
        }

        if (coordinates.getValue()) {
            String coords = "XYZ: " +
                    formatCoord(mc.player.getX()) + Formatting.GRAY + ", " +
                    formatCoord(mc.player.getY()) + Formatting.GRAY + ", " +
                    formatCoord(mc.player.getZ());

            if (nether.getValue()) {
                double x = mc.player.getX();
                double z = mc.player.getZ();
                boolean inNether = mc.world.getRegistryKey().getValue().getPath().contains("nether");
                double convertedX = inNether ? x / 8.0 : x * 8.0;
                double convertedZ = inNether ? z / 8.0 : z * 8.0;
                coords += Formatting.GRAY + " [" + formatCoord(convertedX) + Formatting.GRAY + ", "
                        + formatCoord(convertedZ) + Formatting.GRAY + "]";
            }

            drawText(context, coords, 2, height - 12, getHudColor(height - 12));
        }

        if (direction.getValue()) {
            String dir = getDirection();
            drawText(context, dir, 2, height - 24, getHudColor(height - 24));
        }

        renderMisc(context, width, height);

        if (pearlCooldown.getValue()) {
            float progress = mc.player.getItemCooldownManager().getCooldownProgress(Items.ENDER_PEARL, event.getDelta());
            if (progress > 0.0f) {
                String text = "Pearl cooldown (" + String.format("%.1f", progress * 100.0f) + "%)";
                drawCentered(context, text, width / 2, height / 2 + 14, new Color(170, 80, 255));
            }
        }

        if (healthBar.getValue() != HealthBarMode.None) {
            renderHealthBar(context, width, height);
        }
    }

    private void renderModuleList(DrawContext context, int width) {
        moduleEntries.clear();

        for (Module module : OyVey.moduleManager.modules) {
            if (!module.isEnabled() || !module.isDrawn()) continue;

            String text = module.getFullArrayString();
            moduleEntries.add(text);
        }

        if (sorting.getValue() == Sorting.Alphabetical) {
            moduleEntries.sort(String.CASE_INSENSITIVE_ORDER);
        } else {
            moduleEntries.sort(Comparator.comparingInt((String s) -> mc.textRenderer.getWidth(s)).reversed());
        }

        int y = 2;
        for (String text : moduleEntries) {
            drawText(context, text, width - mc.textRenderer.getWidth(text) - 2, y, getHudColor(y));
            y += 10;
        }
    }

    private void renderArmor(DrawContext context, int width, int height) {
        ItemStack[] armor = {
                mc.player.getEquippedStack(EquipmentSlot.HEAD),
                mc.player.getEquippedStack(EquipmentSlot.CHEST),
                mc.player.getEquippedStack(EquipmentSlot.LEGS),
                mc.player.getEquippedStack(EquipmentSlot.FEET)
        };

        int offset = 0;
        for (ItemStack stack : armor) {
            if (stack.isEmpty()) continue;

            int x = width / 2 + 70 - (18 * offset);
            int y = height - 55;

            context.drawItem(stack, x, y);
            context.drawStackOverlay(mc.textRenderer, stack, x, y);

            if (stack.isDamageable()) {
                int remaining = stack.getMaxDamage() - stack.getDamage();
                String text = (remaining * 100 / stack.getMaxDamage()) + "%";
                Color color = durabilityColor(remaining, stack.getMaxDamage());

                float scale = armorScale.getValue().floatValue();
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(
                        x + 8 - (mc.textRenderer.getWidth(text) * scale) / 2.0f,
                        y - 6 * scale
                );
                context.getMatrices().scale(scale, scale);
                context.drawTextWithShadow(mc.textRenderer, text, 0, 0, color.getRGB());
                context.getMatrices().popMatrix();
            }

            offset++;
        }
    }

    private int renderPotions(DrawContext context, int width, int height, int offset) {
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        effects.sort(Comparator.comparingInt(
                effect -> -mc.textRenderer.getWidth(getPotionText(effect))
        ));

        for (StatusEffectInstance effect : effects) {
            String text = getPotionText(effect);
            int y = height - 12 - (offset * 10);
            drawText(context, text, width - mc.textRenderer.getWidth(text) - 2, y, new Color(effect.getEffectType().value().getColor()));
            offset++;
        }

        return offset;
    }

    private int renderInfoLine(DrawContext context, int width, int height, int offset, String label, String value) {
        String text = label + Formatting.WHITE + " " + value;
        int y = height - 12 - (offset * 10);
        drawText(context, text, width - mc.textRenderer.getWidth(text) - 2, y, getHudColor(y));
        return offset + 1;
    }

    private void renderMisc(DrawContext context, int width, int height) {
        int y = 2;

        if (serverLag.getValue() && OyVey.serverManager.isServerNotResponding()) {
            String text = "Server is currently not responding.";
            drawCentered(context, text, width / 2, y, new Color(255, 100, 100));
            y += 12;
        }

        if (armorWarning.getValue()) {
            int threshold = durabilityThreshold.getValue();

            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            }) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty() || !stack.isDamageable()) continue;

                int remaining = stack.getMaxDamage() - stack.getDamage();
                int percentage = remaining * 100 / stack.getMaxDamage();
                if (percentage > threshold) continue;

                String piece = switch (slot) {
                    case HEAD -> "helmet";
                    case CHEST -> "chestplate";
                    case LEGS -> "leggings";
                    case FEET -> "boots";
                    default -> "armor";
                };

                drawCentered(context, "Your " + piece + " is low!", width / 2, y, new Color(255, 80, 80));
                y += 12;
            }
        }
    }

    private void renderHealthBar(DrawContext context, int width, int height) {
        float maxWidth = 60.0f;
        float barHeight = 2.0f;
        float x = (width - maxWidth) / 2.0f;
        float y = (height + 10.0f) / 2.0f;

        context.fill((int) x, (int) y, (int) (x + maxWidth), (int) (y + barHeight), 0x66000000);

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float maxHealth = mc.player.getMaxHealth() + mc.player.getAbsorptionAmount();
        float target = MathHelper.clamp(health / Math.max(1.0f, maxHealth), 0.0f, 1.0f);

        healthAnimation += (target - healthAnimation) * 0.2f;
        float filled = maxWidth * MathHelper.clamp(healthAnimation, 0.0f, 1.0f);

        Color fillColor = healthBar.getValue() == HealthBarMode.Sync
                ? OyVey.colorManager.getColor()
                : getHealthColor(health);

        context.fill((int) x, (int) y, (int) (x + filled), (int) (y + barHeight), fillColor.getRGB());
        context.drawBorder((int) x, (int) y, (int) maxWidth, (int) barHeight, 0xFF000000);
    }

    private String getPotionText(StatusEffectInstance effect) {
        int amplifier = effect.getAmplifier() + 1;
        String amp = amplifier == 1 ? "" : " " + roman(amplifier);
        String duration = StatusEffectUtil.getDurationText(effect, 1.0f, mc.world.getTickManager().getTickRate()).getString();
        return effect.getEffectType().value().getName().getString() + amp + " " + Formatting.WHITE + duration;
    }

    private String getDirection() {
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        String[] directions = {"South", "South West", "West", "North West", "North", "North East", "East", "South East"};
        String[] axis = {"+Z", "+Z -X", "-X", "-Z -X", "-Z", "-Z +X", "+X", "+Z +X"};

        int index = MathHelper.floorMod(Math.round(yaw / 45.0f), 8);
        return directions[index] + Formatting.GRAY + " [" + Formatting.WHITE + axis[index] + Formatting.GRAY + "]";
    }

    private String formatCoord(double value) {
        return Formatting.WHITE + String.format("%.2f", value);
    }

    private Color durabilityColor(int remaining, int max) {
        float progress = MathHelper.clamp((float) remaining / Math.max(1, max), 0.0f, 1.0f);
        Color high = new Color(0, 255, 0);
        Color low = new Color(255, 0, 0);

        int r = (int) (high.getRed() * progress + low.getRed() * (1.0f - progress));
        int g = (int) (high.getGreen() * progress + low.getGreen() * (1.0f - progress));
        int b = (int) (high.getBlue() * progress + low.getBlue() * (1.0f - progress));
        return new Color(r, g, b);
    }

    private Color getHealthColor(float health) {
        if (health > 18.0f) return new Color(0, 255, 0);
        if (health > 16.0f) return new Color(80, 200, 80);
        if (health > 12.0f) return new Color(255, 255, 0);
        if (health > 8.0f) return new Color(255, 170, 0);
        if (health > 5.0f) return new Color(255, 60, 60);
        return new Color(150, 0, 0);
    }

    private Color getHudColor(float offset) {
        Color base = OyVey.colorManager != null ? OyVey.colorManager.getColor() : Color.WHITE;

        return switch (colorMode.getValue()) {
            case Default -> base;
            case Wave -> {
                float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
                hue = (hue + (offset / 200.0f)) % 1.0f;
                yield Color.getHSBColor(hue, 0.85f, 1.0f);
            }
            case Transition -> {
                Color second = new Color(secondColor.getValue());
                float phase = (float) ((Math.sin((System.currentTimeMillis() / 500.0) + offset / 20.0) + 1.0) * 0.5);
                yield blend(base, second, phase);
            }
        };
    }

    private Color blend(Color a, Color b, float amount) {
        amount = MathHelper.clamp(amount, 0.0f, 1.0f);
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * amount),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * amount),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * amount)
        );
    }

    private void drawText(DrawContext context, String text, int x, int y, Color color) {
        context.drawTextWithShadow(mc.textRenderer, Text.literal(text), x, y, color.getRGB());
    }

    private void drawCentered(DrawContext context, String text, int x, int y, Color color) {
        int width = mc.textRenderer.getWidth(text);
        drawText(context, text, x - width / 2, y, color);
    }

    private String roman(int number) {
        return switch (number) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(number);
        };
    }
}
