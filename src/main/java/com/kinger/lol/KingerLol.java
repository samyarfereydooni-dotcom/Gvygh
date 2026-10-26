package com.kinger.lol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
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

    public static final String NAME = "Kinger";
    public static final String VERSION = "vINFI";

    enum Cat {
        COMBAT("Combat"), MACE("Mace Killer"), CRYSTAL("Crystal"), BASE("Base Finder"),
        SMP("SMP"), MOVEMENT("Movement"), RENDER("Render"), MISC("Misc"), HUD("HUD");
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

    static final Mod KILLAURA = new Mod("Kill Aura", "Silent + GCD", Cat.COMBAT, false, false);
    static final Mod W_TAP = new Mod("Auto W-Tap", "Sprint reset", Cat.COMBAT, true, false);
    static final Mod AIMASSIST = new Mod("Aim Assist", "Smooth aim", Cat.COMBAT, true, false);
    static final Mod CRITICALS = new Mod("Criticals", "Legit crits", Cat.COMBAT, true, false);
    static final Mod HITSELECT = new Mod("Hit Select", "Only real hits", Cat.COMBAT, true, false);
    static final Mod SHIELDBREAKER = new Mod("Shield Breaker", "Axe on shield", Cat.COMBAT, true, false);
    static final Mod VELOCITY = new Mod("Velocity", "Randomized anti-KB", Cat.COMBAT, true, false);
    static final Mod AUTOSWORD = new Mod("Auto Sword", "Best weapon", Cat.COMBAT, true, false);
    static final Mod BOWAIMBOT = new Mod("Bow Aimbot", "Arrow prediction", Cat.COMBAT, true, false);
    static final Mod AUTOBOW = new Mod("Auto Bow Release", "Full charge release", Cat.COMBAT, true, false);
    static final Mod ANTIBOT = new Mod("Anti Bot", "Ignores NPCs", Cat.COMBAT, true, false);

    static final Mod MACE_KILLER = new Mod("Auto Mace Killer", "Pearl catch + smash", Cat.MACE, false, false);
    static final Num MACE_RANGE = new Num("Target Range", "Radius", Cat.MACE, 20.0, 5.0, 50.0, 1.0);
    static final Num PEARL_DELAY = new Num("Pearl Delay", "Ticks pearl to wind", Cat.MACE, 15.0, 5.0, 30.0, 1.0);
    static final Num AIM_SMOOTH = new Num("Aim Smooth", "1=slow 100=instant", Cat.MACE, 25.0, 5.0, 100.0, 5.0);

    static final Mod AUTOCRYSTAL = new Mod("Auto Crystal", "Place+break", Cat.CRYSTAL, false, false);
    static final Mod ANCHOR_AURA = new Mod("Anchor Aura", "Auto anchors", Cat.CRYSTAL, false, false);
    static final Mod AUTOTRAP = new Mod("Auto Trap", "Obsidian trap", Cat.CRYSTAL, false, false);

    static final Mod BASE_FINDER = new Mod("Base Finder", "Chests + tracers", Cat.BASE, false, false);
    static final Mod CHEST_ESP = new Mod("Chest ESP", "Glow chests", Cat.BASE, true, false);
    static final Mod AUTO_MINE_WALK = new Mod("Auto Mine Walk", "Mines + walks", Cat.BASE, false, false);
    static final Mod STORAGE = new Mod("Storage Finder", "Chat list", Cat.BASE, false, true);
    static final Num BASE_RADIUS = new Num("Base Radius", "Scan radius", Cat.BASE, 24, 8, 32, 1);

    static final Mod AUTOTOTEM = new Mod("Auto Totem", "Instant", Cat.SMP, true, false);
    static final Mod AUTOARMOR = new Mod("Auto Armor", "Best armor", Cat.SMP, true, false);
    static final Mod AUTOPOT = new Mod("Auto Pot", "Health", Cat.SMP, true, false);
    static final Mod AUTOGAPPLE = new Mod("Auto Gapple", "Gapple", Cat.SMP, true, false);
    static final Mod AUTORESPAWN = new Mod("Auto Respawn", "Instant", Cat.SMP, true, false);
    static final Mod NOSLOW = new Mod("No Slow", "No slow", Cat.SMP, true, false);
    static final Mod AUTOSHIELD = new Mod("Auto Shield", "Idle shield", Cat.SMP, false, false);
    static final Mod CHESTSTEALER = new Mod("Chest Stealer", "Auto take", Cat.SMP, false, false);
    static final Mod SPAWNER_PROTECT = new Mod("Spawner Protect", "Mine spawner when player near", Cat.SMP, false, false);

    static final Mod SPRINT = new Mod("Auto Sprint", "Sprint", Cat.MOVEMENT, true, false);
    static final Mod NOFALL = new Mod("No Fall", "No fall dmg", Cat.MOVEMENT, true, false);
    static final Mod STEP = new Mod("Step", "Auto step", Cat.MOVEMENT, true, false);
    static final Mod SCAFFOLD = new Mod("God Scaffold", "Legit bridge", Cat.MOVEMENT, false, false);
    static final Mod BUNNYHOP = new Mod("Bunny Hop", "Jump", Cat.MOVEMENT, false, false);
    static final Mod ANTIAFK = new Mod("Anti AFK", "Stay active", Cat.MOVEMENT, true, false);

    static final Mod FULLBRIGHT = new Mod("Fullbright", "See dark", Cat.RENDER, true, false);
    static final Mod PLAYERESP = new Mod("Player ESP", "Glow", Cat.RENDER, true, false);
    static final Mod TRACERS = new Mod("Tracers", "Lines", Cat.RENDER, true, false);

    static final Mod AUTOLOGIN = new Mod("Auto Login", "/login", Cat.MISC, false, true);
    static final Mod AUTOREGISTER = new Mod("Auto Register", "/register", Cat.MISC, false, true);
    static final Mod AUTODISCONNECT = new Mod("Auto Disconnect", "Staff detect", Cat.MISC, false, false);

    static final Mod WATERMARK = new Mod("Watermark", "Client name", Cat.HUD, true, false);
    static final Mod ARRAYLIST = new Mod("ArrayList", "Active", Cat.HUD, true, false);
    static final Mod PING_HUD = new Mod("Ping", "Ping", Cat.HUD, true, false);
    static final Mod FPS_HUD = new Mod("FPS", "FPS", Cat.HUD, true, false);

    static final Num AURA_RANGE = new Num("Aura Range", "Attack range", Cat.COMBAT, 3.1, 3.0, 6.0, 0.1);
    static final Num AIM_SPEED = new Num("Aim Speed", "1-100", Cat.COMBAT, 40, 1, 100, 5);
    static final Num REACH = new Num("Reach", "Reach", Cat.COMBAT, 3.0, 3.0, 3.2, 0.1);
    static final Num AIM_MODE = new Num("Aim Mode", "0=L 1=B 2=R", Cat.COMBAT, 0, 0, 2, 1);

    private static KeyBinding guiKey;
    private static KeyBinding autoCmdKey;
    static String autoCommandText = "/sell hand";
    static Cat lastTab = Cat.MACE;
    private static int totemCooldown = 0, armorCooldown = 0, potCooldown = 0;
    private static int auraCooldown = 0, trapCooldown = 0, stepCooldown = 0, kitCooldown = 0, chestCooldown = 0;
    private static int afkTimer = 0, scaffoldTimer = 0, mineCooldown = 0, baseScanCooldown = 0;
    private static int wTapTimer = 0, spawnerCooldown = 0;
    private static float lastHealth = 20f;
    private static int lastDamageTime = -10, tickCounter = 0;

    private enum MaceState {
        IDLE, EQUIP, JUMP_PEARL, WAIT_PEARL, THROW_WIND, WAIT_BOOST,
        GLIDE, PRE_SMASH, SMASH, COMBO
    }
    private static MaceState maceState = MaceState.IDLE;
    private static int maceTimer = 0, maceStateTicks = 0;
    private static PlayerEntity maceTarget;
    private static boolean elytraEquipped = false;

    private static final List<BlockPos> storageResults = new ArrayList<>();
    private static final List<BlockPos> chestCache = new ArrayList<>();
    private static int storageShowTimer = 0;

    @Override
    public void onInitializeClient() {
        load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("kingerlol", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "KingerLol Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_5, category));
        autoCmdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Kinger Auto Command", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("kingerlol").executes(ctx -> {
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new Menu()));
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(KingerLol::tick);
        HudRenderCallback.EVENT.register(KingerLol::renderHud);
    }

    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("kingerlol.properties"); }
    static void save() {
        try {
            Properties pr = new Properties();
            for (Mod m : MODS) if (!m.action) pr.setProperty("mod." + m.name.replace(" ", "_"), String.valueOf(m.on));
            for (Num n : NUMS) pr.setProperty("num." + n.name.replace(" ", "_"), String.valueOf(n.value));
            pr.setProperty("autocmd", autoCommandText);
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
            String ac = pr.getProperty("autocmd"); if (ac != null) autoCommandText = ac;
        } catch (Exception ignored) {}
    }

    private static int getPing(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return 50;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e != null ? e.getLatency() : 50;
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        tickCounter++;

        while (guiKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new Menu());
        while (autoCmdKey.wasPressed()) {
            if (mc.currentScreen == null && !autoCommandText.isBlank()) {
                mc.setScreen(new ChatScreen(autoCommandText, false));
            }
        }

        float hp = p.getHealth();
        boolean tookDamage = hp < lastHealth - 0.1f;
        if (tookDamage && hp > 0) lastDamageTime = tickCounter;

        if (AUTODISCONNECT.on && mc.getNetworkHandler() != null && tickCounter % 40 == 0) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String name = e.getProfile().name().toLowerCase();
                if (name.contains("staff") || name.contains("admin") || name.contains("owner")) {
                    if (!e.getProfile().id().equals(p.getUuid())) {
                        p.networkHandler.getConnection().disconnect(Text.literal("§cStaff detected"));
                        return;
                    }
                }
            }
        }

        if (ANTIAFK.on) {
            afkTimer++;
            if (afkTimer > 60 && p.isOnGround()) { p.jump(); afkTimer = 0; }
        }

        if (AUTOTOTEM.on) autoTotem(mc, p, tookDamage);
        if (AUTOARMOR.on) autoArmor(mc, p);
        if (AUTOPOT.on) autoPot(mc, p);
        if (AUTOGAPPLE.on) autoGapple(mc, p);
        if (AUTORESPAWN.on && mc.currentScreen instanceof DeathScreen) p.requestRespawn();
        if (CHESTSTEALER.on) chestStealer(mc, p);
        if (SPAWNER_PROTECT.on) spawnerProtect(mc, p);

        if (SPRINT.on && mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.isUsingItem()) mc.options.sprintKey.setPressed(true);
        if (NOSLOW.on) noSlow(p);
        if (AUTOSHIELD.on) autoShield(mc, p);
        if (NOFALL.on) noFall(mc, p);
        if (STEP.on) step(mc, p);
        if (SCAFFOLD.on) scaffold(mc, p);
        if (BUNNYHOP.on && mc.options.forwardKey.isPressed() && p.isOnGround()) p.jump();
        if (AUTO_MINE_WALK.on) autoMineWalk(mc, p);

        if (scaffoldTimer > 0) { scaffoldTimer--; if (scaffoldTimer <= 0) mc.options.backKey.setPressed(false); }
        if (wTapTimer > 0) { wTapTimer--; if (wTapTimer <= 0) mc.options.sprintKey.setPressed(false); }

        if (FULLBRIGHT.on) fullbright(p);
        if (PLAYERESP.on) playerEsp(mc);

        if (BASE_FINDER.on || CHEST_ESP.on) {
            if (baseScanCooldown > 0) baseScanCooldown--;
            else { scanChests(mc, p); baseScanCooldown = 40; }
        }

        if (AUTOSWORD.on) autoSword(mc, p);
        if (BOWAIMBOT.on) bowAimbot(mc, p);
        if (AUTOBOW.on) autoBowRelease(mc, p);
        if (AIMASSIST.on) aimAssist(mc, p);
        if (!MACE_KILLER.on) killAura(mc, p);

        if (VELOCITY.on) {
            float reduce = 0.15f + (new Random().nextFloat() * 0.15f);
            Vec3d v = p.getVelocity();
            if (tookDamage) p.setVelocity(v.x * reduce, v.y, v.z * reduce);
            if (tickCounter - lastDamageTime <= 1 && p.isOnGround() && !p.isJumping()) p.jump();
        }
        lastHealth = hp;

        if (AUTOCRYSTAL.on) autoCrystal(mc, p);
        if (ANCHOR_AURA.on) anchorAura(mc, p);
        if (AUTOTRAP.on) autoTrap(mc, p);
        if (MACE_KILLER.on) maceKiller(mc, p);

        if (storageShowTimer > 0) storageShowTimer--;
    }

    // ============ MACE KILLER (SMOOTH) ============

    private static void maceKiller(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;

        switch (maceState) {
            case IDLE -> {
                if (!isKitReady(p)) { maceState = MaceState.EQUIP; return; }
                if (!p.isOnGround()) return;
                PlayerEntity target = null;
                double bestD = MACE_RANGE.value * MACE_RANGE.value;
                for (PlayerEntity o : mc.world.getPlayers()) {
                    if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
                    double d = p.squaredDistanceTo(o);
                    if (d < bestD) { bestD = d; target = o; }
                }
                if (target == null) return;
                maceTarget = target;
                maceState = MaceState.JUMP_PEARL;
                maceTimer = 0;
                maceStateTicks = 0;
                p.jump();
            }

            case EQUIP -> {
                if (kitCooldown > 0) { kitCooldown--; return; }
                int mS = findItemSlot(p, Items.MACE), wS = findItemSlot(p, Items.WIND_CHARGE);
                int pS = findItemSlot(p, Items.ENDER_PEARL), eS = findItemSlot(p, Items.ELYTRA);
                if (mS == -1 || wS == -1 || pS == -1 || eS == -1) {
                    p.sendMessage(Text.literal("§cMissing: Mace / Wind Charge / Pearl / Elytra"), true);
                    MACE_KILLER.on = false; maceState = MaceState.IDLE; return;
                }
                int sync = p.playerScreenHandler.syncId;
                if (mS != 0) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(mS), 0, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (wS != 1) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(wS), 1, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (pS != 2) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(pS), 2, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (eS != 3) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(eS), 3, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                maceState = MaceState.IDLE;
            }

            case JUMP_PEARL -> {
                maceTimer++;
                // Smoothly look up (pitch → -88)
                smoothLookPitch(p, -88.0f, (float)(AIM_SMOOTH.value / 100.0));
                
                // Once we're looking up enough AND already in air, throw pearl
                if (maceTimer >= 3 && p.getPitch() < -80.0f) {
                    p.getInventory().setSelectedSlot(2);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.WAIT_PEARL;
                    maceTimer = (int) PEARL_DELAY.value;
                }
            }

            case WAIT_PEARL -> {
                // Keep looking up smoothly while pearl is in air
                smoothLookPitch(p, -88.0f, (float)(AIM_SMOOTH.value / 100.0));
                maceTimer--;
                if (maceTimer <= 0) {
                    maceState = MaceState.THROW_WIND;
                    maceTimer = 0;
                }
            }

            case THROW_WIND -> {
                // Look straight up for max boost
                smoothLookPitch(p, -90.0f, 0.6f);
                maceTimer++;
                if (maceTimer >= 2) {
                    p.getInventory().setSelectedSlot(1);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.WAIT_BOOST;
                    maceTimer = 6;
                    maceStateTicks = 0;
                }
            }

            case WAIT_BOOST -> {
                maceTimer--;
                if (maceTimer <= 0) {
                    maceState = MaceState.GLIDE;
                    maceStateTicks = 0;
                }
            }

            case GLIDE -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;

                // Equip elytra if not equipped
                if (!elytraEquipped) {
                    p.getInventory().setSelectedSlot(3);
                    swapChest(mc, p);
                    elytraEquipped = true;
                }
                // Start gliding
                if (!p.isGliding() && !p.isOnGround()) {
                    p.startGliding();
                    p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }

                // Smooth aim toward target
                smoothLook(p, maceTarget.getEyePos(), (float)(AIM_SMOOTH.value / 100.0));

                double dist = p.distanceTo(maceTarget);
                boolean above = p.getY() > maceTarget.getY() + 2.5;

                if ((dist < 7.0 && above) || (above && p.getVelocity().y < -0.3)) {
                    maceState = MaceState.PRE_SMASH;
                    maceTimer = 3;
                    maceStateTicks = 0;
                } else if (p.isOnGround() || maceStateTicks > 120) {
                    resetMace(mc, p);
                }
            }

            case PRE_SMASH -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                
                // Unequip elytra
                if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
                
                // Switch to mace
                p.getInventory().setSelectedSlot(0);
                smoothLook(p, maceTarget.getEyePos(), 0.5f);
                
                maceTimer--;
                if (maceTimer <= 0) {
                    maceState = MaceState.SMASH;
                    maceStateTicks = 0;
                }
            }

            case SMASH -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                
                p.getInventory().setSelectedSlot(0);
                smoothLook(p, maceTarget.getEyePos(), 0.6f);
                
                double dist = p.distanceTo(maceTarget);
                maceStateTicks++;
                
                // Attack repeatedly while in range
                if (dist <= 4.5 && p.getAttackCooldownProgress(0f) >= 0.5f) {
                    mc.interactionManager.attackEntity(p, maceTarget);
                    p.swingHand(Hand.MAIN_HAND);
                }
                
                // Move to combo after 15 ticks or when landed far
                if (maceStateTicks > 15 || (p.isOnGround() && dist > 5.0)) {
                    maceState = MaceState.COMBO;
                    maceStateTicks = 0;
                }
            }

            case COMBO -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;
                p.getInventory().setSelectedSlot(0);
                smoothLook(p, maceTarget.getEyePos(), 0.5f);
                
                if (maceStateTicks < 25) {
                    if (p.distanceTo(maceTarget) <= 4.0 && p.getAttackCooldownProgress(0f) >= 0.9f) {
                        mc.interactionManager.attackEntity(p, maceTarget);
                        p.swingHand(Hand.MAIN_HAND);
                    }
                } else {
                    resetMace(mc, p);
                }
            }
        }
    }

    private static void resetMace(MinecraftClient mc, ClientPlayerEntity p) {
        if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
        maceState = MaceState.IDLE; maceTarget = null; maceTimer = 0; maceStateTicks = 0;
    }

    private static void smoothLook(ClientPlayerEntity p, Vec3d target, float smoothFactor) {
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float yawDiff = wrapDegrees(wantYaw - p.getYaw());
        float pitchDiff = wantPitch - p.getPitch();
        p.setYaw(p.getYaw() + yawDiff * smoothFactor);
        p.setPitch(p.getPitch() + pitchDiff * smoothFactor);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
    }

    private static void smoothLookPitch(ClientPlayerEntity p, float wantPitch, float smoothFactor) {
        float pitchDiff = wantPitch - p.getPitch();
        p.setPitch(p.getPitch() + pitchDiff * smoothFactor);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
    }

    private static boolean isKitReady(ClientPlayerEntity p) {
        return p.getInventory().getStack(0).isOf(Items.MACE) &&
               p.getInventory().getStack(1).isOf(Items.WIND_CHARGE) &&
               p.getInventory().getStack(2).isOf(Items.ENDER_PEARL) &&
               p.getInventory().getStack(3).isOf(Items.ELYTRA);
    }
    private static int findItemSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int invToHandlerSlot(int s) { return s < 9 ? 36 + s : s; }
    private static void swapChest(MinecraftClient mc, ClientPlayerEntity p) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, 6, 3, SlotActionType.SWAP, p);
    }

    // ============ REST OF FUNCTIONS ============

    private static void spawnerProtect(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || spawnerCooldown > 0) { if (spawnerCooldown > 0) spawnerCooldown--; return; }
        boolean danger = false;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || isBot(o, mc)) continue;
            if (p.squaredDistanceTo(o) < 900) { danger = true; break; }
        }
        if (!danger) return;
        BlockPos spawner = null;
        BlockPos base = p.getBlockPos();
        double best = 144;
        for (int dx = -12; dx <= 12; dx++) for (int dy = -6; dy <= 6; dy++) for (int dz = -12; dz <= 12; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            if (mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                double d = p.squaredDistanceTo(pos.toCenterPos());
                if (d < best) { best = d; spawner = pos; }
            }
        }
        if (spawner == null) return;
        Vec3d center = Vec3d.ofCenter(spawner);
        Vec3d eye = p.getEyePos();
        double ddx = center.x - eye.x, ddy = center.y - eye.y, ddz = center.z - eye.z;
        double flat = Math.sqrt(ddx * ddx + ddz * ddz);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(ddz, ddx)) - 90.0);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(ddy, flat));
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, p.isOnGround(), p.horizontalCollision));
        float oldYaw = p.getYaw(), oldPitch = p.getPitch();
        p.setYaw(targetYaw); p.setPitch(targetPitch);
        mc.interactionManager.updateBlockBreakingProgress(spawner, Direction.UP);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oldYaw); p.setPitch(oldPitch);
        spawnerCooldown = 1 + new Random().nextInt(2);
    }

    private static void autoBowRelease(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || !p.isUsingItem()) return;
        if (!p.getActiveItem().isOf(Items.BOW)) return;
        if (p.getItemUseTime() >= 20) mc.interactionManager.stopUsingItem(p);
    }

    private static void scanChests(MinecraftClient mc, ClientPlayerEntity p) {
        chestCache.clear();
        ChunkPos center = p.getChunkPos();
        int radius = (int) BASE_RADIUS.value;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            for (BlockPos pos : chunk.getBlockEntityPositions()) {
                BlockEntity be = mc.world.getBlockEntity(pos);
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                    chestCache.add(pos.toImmutable());
                }
            }
        }
        BlockPos me = p.getBlockPos();
        chestCache.sort(Comparator.comparingDouble(b -> b.getSquaredDistance(me)));
    }

    private static void autoMineWalk(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || mineCooldown > 0) { if (mineCooldown > 0) mineCooldown--; return; }
        Direction facing = p.getHorizontalFacing();
        BlockPos feetPos = p.getBlockPos();
        BlockPos forwardFeet = feetPos.offset(facing);
        BlockPos forwardHead = forwardFeet.up();
        BlockPos forwardFloor = forwardFeet.down();
        BlockPos target = null;
        if (!mc.world.getBlockState(forwardHead).isAir() && isMineable(mc.world.getBlockState(forwardHead))) target = forwardHead;
        else if (!mc.world.getBlockState(forwardFeet).isAir() && isMineable(mc.world.getBlockState(forwardFeet))) target = forwardFeet;
        if (target == null) {
            mc.options.forwardKey.setPressed(true);
            mc.options.attackKey.setPressed(false);
            if (!mc.world.getBlockState(forwardFloor).isSolidBlock(mc.world, forwardFloor)
                && mc.world.getBlockState(feetPos.down()).isSolidBlock(mc.world, feetPos.down())
                && p.isOnGround()) p.jump();
            mineCooldown = 0;
            return;
        }
        Vec3d center = Vec3d.ofCenter(target);
        Vec3d eye = p.getEyePos();
        double dx = center.x - eye.x, dy = center.y - eye.y, dz = center.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)-Math.toDegrees(Math.atan2(dy, flat));
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround(), p.horizontalCollision));
        Direction breakFace = null;
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos neighbor = target.offset(d);
            if (!mc.world.getBlockState(neighbor).isAir()) { breakFace = d; break; }
        }
        if (breakFace == null) breakFace = Direction.UP;
        mc.interactionManager.updateBlockBreakingProgress(target, breakFace);
        p.swingHand(Hand.MAIN_HAND);
        mc.options.forwardKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
        mineCooldown = 1 + new Random().nextInt(2);
    }

    private static boolean isMineable(BlockState st) {
        return !st.isOf(Blocks.BEDROCK) && !st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.END_PORTAL_FRAME)
            && !st.isOf(Blocks.END_PORTAL) && !st.isOf(Blocks.END_GATEWAY) && !st.isOf(Blocks.BARRIER)
            && !st.isOf(Blocks.COMMAND_BLOCK) && !st.isOf(Blocks.CHAIN_COMMAND_BLOCK)
            && !st.isOf(Blocks.REPEATING_COMMAND_BLOCK) && !st.isOf(Blocks.STRUCTURE_BLOCK)
            && !st.isOf(Blocks.JIGSAW) && !st.isOf(Blocks.WATER) && !st.isOf(Blocks.LAVA)
            && !st.isOf(Blocks.NETHER_PORTAL) && !st.isOf(Blocks.REINFORCED_DEEPSLATE);
    }

    private static void scaffold(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || !mc.options.useKey.isPressed()) return;
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).getItem() instanceof BlockItem) { blockSlot = i; break; }
        if (blockSlot == -1) return;
        BlockPos under = p.getBlockPos().down();
        if (mc.world.getBlockState(under).isAir()) {
            int oldSlot = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(blockSlot);
            BlockPos placeAgainst = null;
            Direction placeFace = null;
            Direction behind = p.getHorizontalFacing().getOpposite();
            BlockPos backNeighbor = under.offset(behind);
            if (mc.world.getBlockState(backNeighbor).isSolidBlock(mc.world, backNeighbor)) {
                placeAgainst = backNeighbor;
                placeFace = behind.getOpposite();
            } else {
                for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos neighbor = under.offset(dir);
                    if (mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) {
                        placeAgainst = neighbor;
                        placeFace = dir.getOpposite();
                        break;
                    }
                }
            }
            if (placeAgainst != null) {
                Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(placeFace.getOffsetX() * 0.5, placeFace.getOffsetY() * 0.5, placeFace.getOffsetZ() * 0.5);
                Vec3d eye = p.getEyePos();
                double dx = hitVec.x - eye.x, dy = hitVec.y - eye.y, dz = hitVec.z - eye.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
                p.setSneaking(true);
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, p.isOnGround(), p.horizontalCollision));
                float oldYaw = p.getYaw(), oldPitch = p.getPitch();
                p.setYaw(targetYaw); p.setPitch(targetPitch);
                p.swingHand(Hand.MAIN_HAND);
                BlockHitResult hit = new BlockHitResult(hitVec, placeFace, placeAgainst, false);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                if (!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
                    mc.options.backKey.setPressed(true);
                    scaffoldTimer = 3;
                }
                p.setYaw(oldYaw); p.setPitch(oldPitch);
                p.setSneaking(false);
                p.getInventory().setSelectedSlot(oldSlot);
            }
        }
    }

    static void findStorage(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        scanChests(mc, p);
        storageResults.clear();
        storageResults.addAll(chestCache);
        BlockPos me = p.getBlockPos();
        p.sendMessage(Text.literal("§b═══ BASE FINDER ═══"), false);
        p.sendMessage(Text.literal("§fFound §b" + storageResults.size() + "§f containers"), false);
        int n = Math.min(15, storageResults.size());
        for (int i = 0; i < n; i++) {
            BlockPos b = storageResults.get(i);
            int dist = (int) Math.sqrt(b.getSquaredDistance(me));
            p.sendMessage(Text.literal("§e" + (i+1) + ". §f" + b.getX() + " " + b.getY() + " " + b.getZ() + " §7(" + dist + "m)"), false);
        }
        storageShowTimer = 20 * 30;
    }

    private static void chestStealer(MinecraftClient mc, ClientPlayerEntity p) {
        if (chestCooldown > 0) { chestCooldown--; return; }
        if (!(mc.currentScreen instanceof GenericContainerScreen gcs)) return;
        var handler = gcs.getScreenHandler();
        for (int i = 0; i < handler.slots.size() - 36; i++) {
            if (handler.getSlot(i).getStack().isEmpty()) continue;
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, p);
            chestCooldown = 2;
            return;
        }
    }

    static void autoLogin(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand("login yourpassword");
    }
    static void autoRegister(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendChatCommand("register yourpassword yourpassword");
    }

    private static void noSlow(ClientPlayerEntity p) {
        if (p.isUsingItem() && !p.isOnGround()) {
            Vec3d v = p.getVelocity();
            if (v.horizontalLength() > 0.01 && v.horizontalLength() < 0.15) p.setVelocity(v.x * 1.3, v.y, v.z * 1.3);
        }
    }

    private static void autoShield(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || p.isUsingItem()) return;
        boolean attacking = mc.options.attackKey.isPressed() || (KILLAURA.on && auraCooldown < 3);
        if (attacking) return;
        ItemStack offhand = p.getOffHandStack();
        if (offhand.isOf(Items.SHIELD) && !p.isBlocking() && !p.isSprinting()) mc.options.useKey.setPressed(true);
        else if (!offhand.isOf(Items.SHIELD) && p.isBlocking()) mc.options.useKey.setPressed(false);
    }

    private static boolean isBot(PlayerEntity player, MinecraftClient mc) {
        if (!ANTIBOT.on || mc.getNetworkHandler() == null) return false;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry == null) return true;
        String name = entry.getProfile().name();
        return name.contains("§") || name.length() < 3;
    }

    private static void killAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (!KILLAURA.on || mc.currentScreen != null) { auraCooldown = 0; return; }
        if (auraCooldown > 0) { auraCooldown--; return; }
        PlayerEntity target = null;
        double reachDist = AURA_RANGE.value + REACH.value - 3.0;
        double bestD = reachDist * reachDist;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
            double d = p.squaredDistanceTo(o);
            if (d < bestD) { bestD = d; target = o; }
        }
        if (target == null || p.getAttackCooldownProgress(0f) < 0.9f) return;
        if (HITSELECT.on && p.distanceTo(target) > 3.3) return;

        if (SHIELDBREAKER.on && target.isBlocking()) {
            int axeSlot = -1;
            for (int i = 0; i < 9; i++) {
                Item it = p.getInventory().getStack(i).getItem();
                if (it == Items.NETHERITE_AXE || it == Items.DIAMOND_AXE) { axeSlot = i; break; }
            }
            if (axeSlot != -1) {
                p.getInventory().setSelectedSlot(axeSlot);
                Vec3d eye = p.getEyePos();
                Vec3d pos = target.getEyePos();
                double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, p.isOnGround(), p.horizontalCollision));
                mc.interactionManager.attackEntity(p, target);
                p.swingHand(Hand.MAIN_HAND);
                auraCooldown = 2 + new Random().nextInt(2);
                return;
            }
        }

        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting() && !p.isTouchingWater()) {
            p.jump(); auraCooldown = 2; return;
        }

        if (W_TAP.on && p.isSprinting()) {
            mc.options.sprintKey.setPressed(false);
            wTapTimer = 1;
        }

        Vec3d eye = p.getEyePos();
        Vec3d pos = target.getEyePos();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float jitterYaw = (float) ((Math.random() - 0.5) * 0.08);
        float jitterPitch = (float) ((Math.random() - 0.5) * 0.08);
        float rotYaw = (float) (Math.round((p.getYaw() + wrapDegrees(targetYaw - p.getYaw())) / sens) * sens) + jitterYaw;
        float rotPitch = (float) (Math.round((p.getPitch() + (targetPitch - p.getPitch())) / sens) * sens) + jitterPitch;
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotYaw, rotPitch, p.isOnGround(), p.horizontalCollision));
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        int ping = getPing(mc);
        auraCooldown = Math.max(1, (ping / 50) + 1 + new Random().nextInt(2));
    }

    private static void aimAssist(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || KILLAURA.on || MACE_KILLER.on) return;
        Vec3d eye = p.getEyePos();
        PlayerEntity target = null;
        double bestAngle = 35.0;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
            double d = p.distanceTo(o);
            if (d > 8.0) continue;
            Vec3d tEye = o.getEyePos();
            double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
            float angle = Math.max(Math.abs(wrapDegrees(wantYaw - p.getYaw())), Math.abs(wantPitch - p.getPitch()));
            if (angle < bestAngle) { bestAngle = angle; target = o; }
        }
        if (target == null) return;
        Vec3d tEye = target.getEyePos();
        double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float speedF = (float) Math.min(1.0, AIM_SPEED.value / 100.0);
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float finalYaw = p.getYaw() + wrapDegrees(wantYaw - p.getYaw()) * speedF;
        float finalPitch = p.getPitch() + (wantPitch - p.getPitch()) * speedF;
        finalYaw += (float) ((Math.random() - 0.5) * 0.06);
        finalPitch += (float) ((Math.random() - 0.5) * 0.06);
        finalYaw = (float) (Math.round(finalYaw / sens) * sens);
        finalPitch = (float) (Math.round(finalPitch / sens) * sens);
        p.setYaw(finalYaw); p.setPitch(finalPitch);
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static int swordRank(Item item) {
        if (item == Items.NETHERITE_SWORD) return 6;
        if (item == Items.DIAMOND_SWORD) return 5;
        if (item == Items.IRON_SWORD) return 4;
        if (item == Items.STONE_SWORD) return 3;
        if (item == Items.GOLDEN_SWORD) return 2;
        if (item == Items.WOODEN_SWORD) return 1;
        if (item == Items.NETHERITE_AXE) return 7;
        if (item == Items.DIAMOND_AXE) return 4;
        return 0;
    }

    private static void autoSword(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || mc.options.attackKey.isPressed() || MACE_KILLER.on) return;
        PlayerEntity target = null;
        double bestD = 36;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || isBot(o, mc)) continue;
            double d = p.squaredDistanceTo(o);
            if (d < bestD) { bestD = d; target = o; }
        }
        if (target == null) return;
        int bestSlot = -1, bestRank = 0;
        for (int i = 0; i < 9; i++) {
            int r = swordRank(p.getInventory().getStack(i).getItem());
            if (r > bestRank) { bestRank = r; bestSlot = i; }
        }
        if (bestSlot != -1 && p.getInventory().getSelectedSlot() != bestSlot) p.getInventory().setSelectedSlot(bestSlot);
    }

    private static void bowAimbot(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || !p.isUsingItem()) return;
        ItemStack active = p.getActiveItem();
        if (!(active.isOf(Items.BOW) || active.isOf(Items.CROSSBOW))) return;
        PlayerEntity target = null;
        double bestD = 2500;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
            double d = p.squaredDistanceTo(o);
            if (d < bestD) { bestD = d; target = o; }
        }
        if (target == null) return;
        Vec3d eye = p.getEyePos();
        Vec3d tPos = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.6, target.getZ());
        double dist = eye.distanceTo(tPos);
        Vec3d tv = target.getVelocity();
        double time = dist / 3.0;
        Vec3d predicted = tPos.add(tv.multiply(time, time, time));
        double drop = 0.05 * time * time * 9.8;
        lookAt(p, predicted.add(0, drop, 0));
    }

    private static void autoCrystal(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity c && p.distanceTo(c) < 5.0) {
                lookAt(p, new Vec3d(c.getX(), c.getY(), c.getZ()));
                mc.interactionManager.attackEntity(p, c);
                p.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
        int crystalSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) { crystalSlot = i; break; }
        }
        if (crystalSlot == -1) return;
        BlockPos placePos = findCrystalSpot(mc, p);
        if (placePos == null) return;
        p.getInventory().setSelectedSlot(crystalSlot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(placePos), Direction.UP, placePos, false);
        lookAt(p, Vec3d.ofCenter(placePos));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static BlockPos findCrystalSpot(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -2; dy <= 3; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            BlockState st = mc.world.getBlockState(pos);
            if ((st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK)) && mc.world.isAir(pos.up())) {
                if (p.squaredDistanceTo(pos.toCenterPos()) <= 25) return pos;
            }
        }
        return null;
    }

    private static void anchorAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        BlockPos anchor = findAnchor(mc, p);
        if (anchor == null) return;
        if (mc.world.getBlockState(anchor).isOf(Blocks.RESPAWN_ANCHOR)) {
            int glowSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getStack(i).isOf(Items.GLOWSTONE)) { glowSlot = i; break; }
            }
            if (glowSlot != -1) {
                p.getInventory().setSelectedSlot(glowSlot);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
                lookAt(p, Vec3d.ofCenter(anchor));
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                p.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
        lookAt(p, Vec3d.ofCenter(anchor));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static BlockPos findAnchor(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -3; dy <= 3; dy++) for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                if (p.squaredDistanceTo(pos.toCenterPos()) <= 36) return pos;
            }
        }
        return null;
    }

    private static void autoTrap(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || trapCooldown > 0) { if (trapCooldown > 0) trapCooldown--; return; }
        PlayerEntity target = null;
        double bestD = 25;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || isBot(o, mc)) continue;
            double d = p.squaredDistanceTo(o);
            if (d < bestD) { bestD = d; target = o; }
        }
        if (target == null) return;
        int blockSlot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.OBSIDIAN)) { blockSlot = i; break; }
        if (blockSlot == -1) return;
        BlockPos tPos = target.getBlockPos();
        BlockPos[] surrounds = { tPos.north(), tPos.south(), tPos.east(), tPos.west(), tPos.up(), tPos.up(2) };
        p.getInventory().setSelectedSlot(blockSlot);
        for (BlockPos pos : surrounds) {
            if (mc.world.getBlockState(pos).isAir()) {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                lookAt(p, Vec3d.ofCenter(pos));
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                p.swingHand(Hand.MAIN_HAND);
                trapCooldown = 2;
                return;
            }
        }
    }

    private static void autoTotem(MinecraftClient mc, ClientPlayerEntity p, boolean tookDamage) {
        if (totemCooldown > 0) { totemCooldown--; return; }
        if (p.currentScreenHandler != p.playerScreenHandler || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        int cd = tookDamage ? 0 : 5;
        for (int slot = 9; slot <= 44; slot++) {
            if (p.playerScreenHandler.getSlot(slot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                totemCooldown = cd;
                return;
            }
        }
    }

    private static void autoArmor(MinecraftClient mc, ClientPlayerEntity p) {
        if (armorCooldown > 0) { armorCooldown--; return; }
        if (mc.currentScreen != null || p.currentScreenHandler != p.playerScreenHandler) return;
        if (p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            for (int slot = 9; slot <= 44; slot++) {
                ItemStack st = p.playerScreenHandler.getSlot(slot).getStack();
                EquippableComponent eq = st.get(DataComponentTypes.EQUIPPABLE);
                if (eq != null && eq.slot() == EquipmentSlot.CHEST) {
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, p);
                    armorCooldown = 10;
                    return;
                }
            }
        }
    }

    private static void autoPot(MinecraftClient mc, ClientPlayerEntity p) {
        if (potCooldown > 0) { potCooldown--; return; }
        if (mc.currentScreen != null || p.isUsingItem() || p.getHealth() > 14) return;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.POTION)) {
                int old = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(i);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.getInventory().setSelectedSlot(old);
                potCooldown = 40;
                return;
            }
        }
    }

    private static void autoGapple(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || p.isUsingItem() || p.getHealth() > 8) return;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(Items.ENCHANTED_GOLDEN_APPLE) || st.isOf(Items.GOLDEN_APPLE)) {
                int old = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(i);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.getInventory().setSelectedSlot(old);
                return;
            }
        }
    }

    private static void step(MinecraftClient mc, ClientPlayerEntity p) {
        if (stepCooldown > 0) { stepCooldown--; return; }
        if (!p.isOnGround() || !p.horizontalCollision) return;
        if (!(mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())) return;
        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, 0.42, v.z);
        stepCooldown = 4;
    }

    private static void noFall(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.fallDistance > 2.0f) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            p.fallDistance = 0f;
        }
    }

    private static void fullbright(ClientPlayerEntity p) {
        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 220) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
        }
    }

    private static void playerEsp(MinecraftClient mc) {
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == mc.player) continue;
            if (isBot(o, mc)) { o.setGlowing(false); continue; }
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

    private static int[] projectToScreen(Vec3d worldPos, Vec3d camPos, float yaw, float pitch, int sw, int sh, double focal) {
        Vec3d delta = worldPos.subtract(camPos);
        double cosY = Math.cos(Math.toRadians(-yaw));
        double sinY = Math.sin(Math.toRadians(-yaw));
        Vec3d r1 = new Vec3d(delta.x * cosY + delta.z * sinY, delta.y, -delta.x * sinY + delta.z * cosY);
        double cosP = Math.cos(Math.toRadians(-pitch));
        double sinP = Math.sin(Math.toRadians(-pitch));
        Vec3d r2 = new Vec3d(r1.x, r1.y * cosP - r1.z * sinP, r1.y * sinP + r1.z * cosP);
        if (r2.z > -0.1) return null;
        int sx = (int) (sw / 2.0 + (r2.x / -r2.z) * focal);
        int sy = (int) (sh / 2.0 - (r2.y / -r2.z) * focal);
        if (sx < -100 || sx > sw + 100 || sy < -100 || sy > sh + 100) return null;
        return new int[]{sx, sy};
    }

    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int steps = 0;
        while (steps < 2000) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
            steps++;
        }
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden || mc.currentScreen instanceof Menu) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        Vec3d camPos = p.getEyePos();
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float
