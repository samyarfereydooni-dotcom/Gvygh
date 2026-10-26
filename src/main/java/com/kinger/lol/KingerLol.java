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

    static final Mod MACE_KILLER = new Mod("Auto Mace Killer", "Pearl catch combo", Cat.MACE, false, false);
    static final Num MACE_RANGE = new Num("Target Range", "Radius", Cat.MACE, 20.0, 5.0, 50.0, 1.0);
    static final Num TELEPORT_DELAY = new Num("Teleport Delay", "Ticks after pearl", Cat.MACE, 5.0, 2.0, 15.0, 1.0);

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
        IDLE, EQUIP, PEARL_WIND, WAIT_TELEPORT, GLIDE, SMASH, COMBO, RESET
    }
    private static MaceState maceState = MaceState.IDLE;
    private static int maceTimer = 0, maceStateTicks = 0;
    private static PlayerEntity maceTarget;
    private static boolean elytraEquipped = false;
    private static float preStateYaw = 0;

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

        if (scaffoldTimer > 0) {
            scaffoldTimer--;
            if (scaffoldTimer <= 0) mc.options.backKey.setPressed(false);
        }
        if (wTapTimer > 0) {
            wTapTimer--;
            if (wTapTimer <= 0) mc.options.sprintKey.setPressed(false);
        }

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
        if (MACE_KILLER.on) maceKillerFixed(mc, p);

        if (storageShowTimer > 0) storageShowTimer--;
    }

    private static void maceKillerFixed(MinecraftClient mc, ClientPlayerEntity p) {
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
                maceState = MaceState.PEARL_WIND;
                maceTimer = 0;
                maceStateTicks = 0;
                preStateYaw = p.getYaw();
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

            case PEARL_WIND -> {
                p.setPitch(-90.0f);
                p.setYaw(preStateYaw);
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(preStateYaw, -90.0f, p.isOnGround(), p.horizontalCollision));

                p.getInventory().setSelectedSlot(2);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);

                p.getInventory().setSelectedSlot(1);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);

                maceState = MaceState.WAIT_TELEPORT;
                maceTimer = (int) (TELEPORT_DELAY.value + new Random().nextInt(3));
                maceStateTicks = 0;
            }

            case WAIT_TELEPORT -> {
                p.setPitch(-90.0f);
                maceTimer--;
                if (maceTimer <= 0) {
                    maceState = MaceState.GLIDE;
                    maceStateTicks = 0;
                }
            }

            case GLIDE -> {
                if (maceTarget == null || !maceTarget.isAlive()) {
                    resetMace(mc, p);
                    return;
                }
                maceStateTicks++;
                lookAt(p, maceTarget.getEyePos());

                if (!elytraEquipped) {
                    p.getInventory().setSelectedSlot(3);
                    swapChest(mc, p);
                    elytraEquipped = true;
                }
                if (!p.isGliding()) {
                    p.startGliding();
                    p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }

                double dist = p.distanceTo(maceTarget);
                if (dist < 6.0 || (p.getY() > maceTarget.getY() + 3.0 && p.getVelocity().y < 0)) {
                    maceState = MaceState.SMASH;
                    maceTimer = 2;
                    maceStateTicks = 0;
                } else if (p.isOnGround() || maceStateTicks > 120) {
                    resetMace(mc, p);
                }
            }

            case SMASH -> {
                if (maceTimer > 0) { maceTimer--; return; }
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }

                if (elytraEquipped) {
                    swapChest(mc, p);
                    elytraEquipped = false;
                }

                p.getInventory().setSelectedSlot(0);
                lookAt(p, maceTarget.getEyePos());

                if (p.distanceTo(maceTarget) <= 3.2 && p.getAttackCooldownProgress(0f) >= 0.85f) {
                    mc.interactionManager.attackEntity(p, maceTarget);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.COMBO;
                    maceStateTicks = 0;
                } else if (p.isOnGround()) {
                    resetMace(mc, p);
                }
            }

            case COMBO -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;
                if (maceStateTicks < 20) {
                    p.getInventory().setSelectedSlot(0);
                    lookAt(p, maceTarget.getEyePos());
                    if (p.distanceTo(maceTarget) <= 3.5 && p.getAttackCooldownProgress(0f) >= 0.9f) {
                        mc.interactionManager.attackEntity(p, maceTarget);
                        p.swingHand(Hand.MAIN_HAND);
                    }
                } else {
                    resetMace(mc, p);
                }
            }

            case RESET -> resetMace(mc, p);
        }
    }

    private static void resetMace(MinecraftClient mc, ClientPlayerEntity p) {
        if (elytraEquipped) {
            swapChest(mc, p);
            elytraEquipped = false;
        }
        maceState = MaceState.IDLE;
        maceTarget = null;
        maceTimer = 0;
        maceStateTicks = 0;
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

    // ... (بقیه توابع مثل قبل: spawnerProtect, autoBowRelease, scanChests, autoMineWalk, scaffold, findStorage, chestStealer, autoLogin, autoRegister, noSlow, autoShield, isBot, killAura, aimAssist, wrapDegrees, swordRank, autoSword, bowAimbot, autoCrystal, findCrystalSpot, anchorAura, findAnchor, autoTrap, autoTotem, autoArmor, autoPot, autoGapple, step, noFall, fullbright, playerEsp, lookAt, projectToScreen, drawLine, renderHud, Menu, ...)
                                       }
