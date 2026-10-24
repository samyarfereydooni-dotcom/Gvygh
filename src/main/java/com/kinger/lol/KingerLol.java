package com.kinger.lol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class KingerLol implements ClientModInitializer {

    public static final String NAME = "KingerLol GodMode";
    public static final String VERSION = "v7.0";

    enum Cat {
        COMBAT("Combat"), MOVEMENT("Movement"), RENDER("Render"), EXPLOIT("Exploit"), HUD("HUD");
        final String label;
        Cat(String label) { this.label = label; }
    }
    
    static final List<Mod> MODS = new ArrayList<>();
    static final List<Num> NUMS = new ArrayList<>();

    static final class Mod {
        final String name, desc; final Cat cat; final boolean action; boolean on;
        Mod(String name, String desc, Cat cat, boolean on, boolean action) {
            this.name = name; this.desc = desc; this.cat = cat; this.on = on; this.action = action; MODS.add(this);
        }
    }
    static final class Num {
        final String name, desc; final Cat cat; final double min, max, step; double value;
        Num(String name, String desc, Cat cat, double value, double min, double max, double step) {
            this.name = name; this.desc = desc; this.cat = cat; this.value = value; this.min = min; this.max = max; this.step = step; NUMS.add(this);
        }
        void change(int dir) { value = Math.max(min, Math.min(max, value + dir * step)); }
    }

    static final Mod KILLAURA = new Mod("Kill Aura", "Auto attacks entities", Cat.COMBAT, false, false);
    static final Mod SILENT_AIM = new Mod("Silent Aim", "Attacks without looking", Cat.COMBAT, true, false);
    static final Mod VELOCITY = new Mod("Velocity", "Reduces knockback", Cat.COMBAT, true, false);
    static final Mod CRITICALS = new Mod("Criticals", "Always crit", Cat.COMBAT, true, false);
    static final Mod AUTO_TOTEM = new Mod("Auto Totem", "Offhand totem", Cat.COMBAT, true, false);
    static final Mod AUTO_ARMOR = new Mod("Auto Armor", "Best armor", Cat.COMBAT, true, false);
    
    static final Mod SCAFFOLD = new Mod("Scaffold", "Auto bridge", Cat.MOVEMENT, false, false);
    static final Mod SPRINT = new Mod("Auto Sprint", "Always sprint", Cat.MOVEMENT, true, false);
    static final Mod NOFALL = new Mod("No Fall", "No fall damage", Cat.MOVEMENT, true, false);
    
    static final Mod FULLBRIGHT = new Mod("Fullbright", "See in dark", Cat.RENDER, true, false);
    static final Mod PLAYER_ESP = new Mod("Player ESP", "Glow players", Cat.RENDER, true, false);
    
    static final Mod STORAGE = new Mod("Storage Finder", "Finds chests", Cat.EXPLOIT, false, true);

    static final Mod WATERMARK = new Mod("Watermark", "Shows client", Cat.HUD, true, false);
    static final Mod ARRAYLIST = new Mod("ArrayList", "Active modules", Cat.HUD, true, false);

    static final Num AURA_RANGE = new Num("Aura Range", "Attack distance", Cat.COMBAT, 4.5, 3.0, 6.0, 0.5);
    static final Num REACH = new Num("Reach", "Reach distance", Cat.COMBAT, 4.5, 3.0, 6.0, 0.5);

    private static KeyBinding guiKey;
    static Cat lastTab = Cat.COMBAT;
    private static int totemCooldown = 0, armorCooldown = 0, auraCooldown = 0;
    private static float lastHealth = 20f;

    @Override
    public void onInitializeClient() {
        load();
        
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("kingerlol", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("KingerLol Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        
        ClientTickEvents.END_CLIENT_TICK.register(KingerLol::tick);
        HudRenderCallback.EVENT.register(KingerLol::renderHud);
    }

    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("kingerlol.properties"); }
    static void save() {
        try {
            Properties pr = new Properties();
            for (Mod m : MODS) if (!m.action) pr.setProperty("mod." + m.name.replace(" ", "_"), String.valueOf(m.on));
            for (Num n : NUMS) pr.setProperty("num." + n.name.replace(" ", "_"), String.valueOf(n.value));
            try (OutputStream os = Files.newOutputStream(configPath())) { pr.store(os, NAME); }
        } catch (Exception ignored) {}
    }
    private static void load() {
        try {
            Path path = configPath(); if (!Files.exists(path)) return;
            Properties pr = new Properties();
            try (InputStream is = Files.newInputStream(path)) { pr.load(is); }
            for (Mod m : MODS) { String v = pr.getProperty("mod." + m.name.replace(" ", "_")); if (v != null) m.on = Boolean.parseBoolean(v); }
            for (Num n : NUMS) { String v = pr.getProperty("num." + n.name.replace(" ", "_")); if (v != null) n.value = Double.parseDouble(v.trim()); }
        } catch (Exception ignored) {}
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        while (guiKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new Menu());

        if (AUTO_TOTEM.on) autoTotem(mc, p);
        if (AUTO_ARMOR.on) autoArmor(mc, p);
        if (SPRINT.on && mc.options.forwardKey.isPressed() && !p.isSneaking()) mc.options.sprintKey.setPressed(true);
        
        if (FULLBRIGHT.on) fullbright(p);
        if (PLAYER_ESP.on) playerEsp(mc);
        
        killAura(mc, p);
        velocity(mc, p);
        scaffold(mc, p);
        noFall(mc, p);
    }

    private static void killAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!KILLAURA.on || mc.currentScreen != null) { auraCooldown = 0; return; }
        if (auraCooldown > 0) { auraCooldown--; return; }

        PlayerEntity target = null;
        double bestD = AURA_RANGE.value * AURA_RANGE.value;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            double d = p.squaredDistanceTo(o);
            if (d < bestD) { bestD = d; target = o; }
        }
        if (target == null || p.getAttackCooldownProgress(0f) < 0.9f) return;

        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting() && !p.isTouchingWater()) {
            p.jump(); auraCooldown = 2; return;
        }

        if (SILENT_AIM.on) {
            float oldYaw = p.getYaw(); float oldPitch = p.getPitch();
            lookAt(p, target.getEyePos());
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
            p.setYaw(oldYaw); p.setPitch(oldPitch);
        } else {
            lookAt(p, target.getEyePos());
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
        auraCooldown = 2;
    }

    private static void velocity(MinecraftClient mc, ClientPlayerEntity p) {
        if (!VELOCITY.on) { lastHealth = p.getHealth(); return; }
        float hp = p.getHealth();
        if (hp < lastHealth - 0.1f && hp > 0) {
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x * 0.2, v.y, v.z * 0.2);
        }
        lastHealth = hp;
    }

    private static void autoTotem(MinecraftClient mc, ClientPlayerEntity p) {
        if (totemCooldown > 0) { totemCooldown--; return; }
        if (p.currentScreenHandler != p.playerScreenHandler || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        for (int slot = 9; slot <= 44; slot++) {
            if (p.playerScreenHandler.getSlot(slot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                totemCooldown = 5; return;
            }
        }
    }

    private static void autoArmor(MinecraftClient mc, ClientPlayerEntity p) {
        if (armorCooldown > 0) { armorCooldown--; return; }
        if (mc.currentScreen != null || p.currentScreenHandler != p.playerScreenHandler) return;
        if (p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack st = p.playerScreenHandler.getSlot(slot).getStack();
                EquippableComponent equippable = st.get(DataComponentTypes.EQUIPPABLE);
                if (equippable != null && equippable.slot() == EquipmentSlot.CHEST) {
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, p);
                    armorCooldown = 10; return;
                }
            }
        }
    }

    private static void scaffold(MinecraftClient mc, ClientPlayerEntity p) {
        if (!SCAFFOLD.on || mc.currentScreen != null || p.isOnGround() || p.getVelocity().y > 0) return;
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() instanceof BlockItem) { blockSlot = i; break; }
        }
        if (blockSlot == -1) return;
        
        p.getInventory().setSelectedSlot(blockSlot);
        
        BlockPos under = p.getBlockPos().down();
        if (mc.world.getBlockState(under).isAir()) {
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(under), Direction.UP, under, false);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        }
    }

    private static void noFall(MinecraftClient mc, ClientPlayerEntity p) {
        if (!NOFALL.on) return;
        if (p.fallDistance > 2.0f) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            p.fallDistance = 0f;
        }
    }

    private static void fullbright(ClientPlayerEntity p) {
        if (FULLBRIGHT.on) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 220) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
            }
        }
    }

    private static void playerEsp(MinecraftClient mc) {
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player) continue;
            o.setGlowing(true);
        }
    }

    private static void lookAt(ClientPlayerEntity p, Vec3d pos) {
        Vec3d eye = p.getEyePos();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, flat)));
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof Menu) return;
        TextRenderer tr = mc.textRenderer;
        int y = 4;
        if (WATERMARK.on) {
            ctx.drawTextWithShadow(tr, NAME + " " + VERSION, 4, y, 0xFF00FFFF); y += 12;
        }
        if (ARRAYLIST.on) {
            int sw = ctx.getScaledWindowWidth();
            List<Mod> active = new ArrayList<>();
            for (Mod m : MODS) if (m.on && !m.action && m.cat != Cat.HUD) active.add(m);
            active.sort(Comparator.comparingInt((Mod m) -> -tr.getWidth(m.name)));
            for (Mod m : active) {
                ctx.drawTextWithShadow(tr, m.name, sw - tr.getWidth(m.name) - 4, y, 0xFF55FF55);
                y += 11;
            }
        }
    }

    enum Kind { TAB, TOGGLE, ACTION, STEP, VALUE }
    record Widget(int x, int y, int w, int h, String label, Kind kind, BooleanSupplier active, String desc, Supplier<String> tag) {}

    static class Menu extends Screen {
        static final int PW = 460, PH = 350;
        private final List<Widget> widgets = new ArrayList<>();
        Menu() { super(Text.literal(NAME)); }
        private int px() { return (this.width - PW) / 2; }
        private int py() { return (this.height - PH) / 2; }
        private void hit(int x, int y, int w, int h, Runnable action) {
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> action.run()).dimensions(x, y, w, h).build());
        }

        @Override protected void init() {
            widgets.clear();
            int px = px(), py = py();
            int sx = px + 8, sy = py + 42, i = 0;
            for (Cat c : Cat.values()) {
                final Cat cc = c; int ty = sy + i * 28;
                hit(sx, ty, 100, 24, () -> { lastTab = cc; save(); clearAndInit(); });
                widgets.add(new Widget(sx, ty, 100, 24, c.label, Kind.TAB, () -> lastTab == cc, null, null));
                i++;
            }
            int cx = px + 116, cy = py + 42, col = 0, row = 0;
            for (Mod m : MODS) {
                if (m.cat != lastTab) continue;
                int mx = cx + col * 172, my = cy + row * 30;
                hit(mx, my, 168, 26, () -> {
                    if (m.action) { if (m == STORAGE) { close(); findStorage(this.client); } } else { m.on = !m.on; save(); }
                });
                widgets.add(new Widget(mx, my, 168, 26, m.name, m.action ? Kind.ACTION : Kind.TOGGLE, () -> m.on, m.desc, m.action ? () -> "RUN" : null));
                col++; if (col == 2) { col = 0; row++; }
            }
            if (col == 1) row++;
            for (Num n : NUMS) {
                if (n.cat != lastTab) continue;
                int ny = cy + row * 30;
                widgets.add(new Widget(cx, ny, 250, 26, n.name, Kind.VALUE, () -> false, n.desc, () -> String.valueOf(n.value)));
                hit(cx + 254, ny, 22, 26, () -> { n.change(-1); save(); });
                widgets.add(new Widget(cx + 254, ny, 22, 26, "-", Kind.STEP, () -> false, null, null));
                hit(cx + 278, ny, 22, 26, () -> { n.change(1); save(); });
                widgets.add(new Widget(cx + 278, ny, 22, 26, "+", Kind.STEP, () -> false, null, null));
                row++;
            }
        }

        @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, this.width, this.height, 0xA0000000);
            int px = px(), py = py();
            ctx.fill(px, py, px + PW, py + PH, 0xFF101018);
            ctx.fill(px, py, px + PW, py + 32, 0xFF1A1A26);
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            TextRenderer tr = this.textRenderer;
            int px = px(), py = py();
            ctx.drawTextWithShadow(tr, NAME + " " + VERSION, px + 12, py + 12, 0xFF00FFFF);
            for (Widget w : widgets) {
                boolean hov = mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() && mouseY < w.y() + w.h();
                boolean on = w.active().getAsBoolean();
                int bg = on && (w.kind() == Kind.TOGGLE || w.kind() == Kind.TAB) ? 0xFF2E2E3E : (hov ? 0xFF333344 : 0xFF23232E);
                ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                int ty = w.y() + (w.h() - 8) / 2;
                ctx.drawTextWithShadow(tr, w.label(), w.x() + 8, ty, on ? 0xFF55FF55 : 0xFFBBBBCC);
                if (w.tag() != null) {
                    String tag = w.tag().get();
                    ctx.drawTextWithShadow(tr, tag, w.x() + w.w() - tr.getWidth(tag) - 8, ty, on ? 0xFF55FF55 : 0xFFFF5566);
                }
            }
        }
        @Override public boolean shouldPause() { return false; }
    }

    static void findStorage(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player; if (p == null || mc.world == null) return;
        List<BlockPos> found = new ArrayList<>();
        ChunkPos center = p.getChunkPos();
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity be = mc.world.getBlockEntity(pos);
                if (be instanceof ChestBlockEntity) found.add(pos.toImmutable());
            }
        }
        p.sendMessage(Text.literal(NAME + " | Chests found: " + found.size()), false);
    }
                    }
