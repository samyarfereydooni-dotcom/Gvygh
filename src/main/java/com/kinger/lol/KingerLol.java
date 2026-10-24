package com.kinger.lol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    public static final String VERSION = "v500Q";

    enum Cat {
        COMBAT("Combat"), MACE("Mace Killer"), CRYSTAL("Crystal"),
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

    // --- COMBAT ---
    static final Mod KILLAURA = new Mod("Kill Aura", "Silent attacks + smoothing", Cat.COMBAT, false, false);
    static final Mod AIMASSIST = new Mod("Aim Assist", "Smooth legit aim", Cat.COMBAT, true, false);
    static final Mod CRITICALS = new Mod("Criticals", "Smart crits", Cat.COMBAT, true, false);
    static final Mod HITSELECT = new Mod("Hit Select", "Only attack when hit lands", Cat.COMBAT, true, false);
    static final Mod SHIELDBREAKER = new Mod("Shield Breaker", "Axe when shield up", Cat.COMBAT, true, false);
    static final Mod VELOCITY = new Mod("Velocity", "Anti-KB + jump reset", Cat.COMBAT, true, false);
    static final Mod AUTOSWORD = new Mod("Auto Sword", "Best weapon", Cat.COMBAT, true, false);
    static final Mod BOWAIMBOT = new Mod("Bow Aimbot", "Predict arrow", Cat.COMBAT, true, false);
    static final Mod ANTIBOT = new Mod("Anti Bot", "Ignores NPCs", Cat.COMBAT, true, false);
    static final Mod SPRINTRESET = new Mod("Sprint Reset", "For combos", Cat.COMBAT, true, false);

    // --- MACE ---
    static final Mod MACE_KILLER = new Mod("Auto Mace Killer", "All-in-one combo", Cat.MACE, false, false);
    static final Num MACE_RANGE = new Num("Target Range", "Search radius", Cat.MACE, 20.0, 5.0, 50.0, 1.0);
    static final Num PEARL_DELAY = new Num("Pearl Delay", "Ticks", Cat.MACE, 3.0, 1.0, 10.0, 1.0);

    // --- CRYSTAL ---
    static final Mod AUTOCRYSTAL = new Mod("Auto Crystal", "Place & break", Cat.CRYSTAL, false, false);
    static final Mod ANCHOR_AURA = new Mod("Anchor Aura", "Auto anchors", Cat.CRYSTAL, false, false);
    static final Mod AUTOTRAP = new Mod("Auto Trap", "Obsidian trap", Cat.CRYSTAL, false, false);

    // --- SMP ---
    static final Mod AUTOTOTEM = new Mod("Auto Totem", "Instant offhand + fallback", Cat.SMP, true, false);
    static final Mod AUTOARMOR = new Mod("Auto Armor", "Best armor", Cat.SMP, true, false);
    static final Mod AUTOPOT = new Mod("Auto Pot", "Health pot", Cat.SMP, true, false);
    static final Mod AUTOGAPPLE = new Mod("Auto Gapple", "Gapple", Cat.SMP, true, false);
    static final Mod AUTORESPAWN = new Mod("Auto Respawn", "Instant", Cat.SMP, true, false);
    static final Mod NOSLOW = new Mod("No Slow", "No slow effect", Cat.SMP, true, false);
    static final Mod AUTOSHIELD = new Mod("Auto Shield", "Shield idle", Cat.SMP, false, false);
    static final Mod CHESTSTEALER = new Mod("Chest Stealer", "Auto take items", Cat.SMP, false, false);
    static final Mod STORAGE = new Mod("Storage Finder", "Advanced locate", Cat.SMP, false, true);

    // --- MOVEMENT ---
    static final Mod SPRINT = new Mod("Auto Sprint", "Always sprint", Cat.MOVEMENT, true, false);
    static final Mod NOFALL = new Mod("No Fall", "No fall damage", Cat.MOVEMENT, true, false);
    static final Mod STEP = new Mod("Step", "Auto step", Cat.MOVEMENT, true, false);
    static final Mod SCAFFOLD = new Mod("Scaffold", "Auto bridge", Cat.MOVEMENT, false, false);
    static final Mod BUNNYHOP = new Mod("Bunny Hop", "Auto jump", Cat.MOVEMENT, false, false);
    static final Mod ANTIAFK = new Mod("Anti AFK", "Stay active", Cat.MOVEMENT, true, false);

    // --- RENDER ---
    static final Mod FULLBRIGHT = new Mod("Fullbright", "See in dark", Cat.RENDER, true, false);
    static final Mod PLAYERESP = new Mod("Player ESP", "Glow players", Cat.RENDER, true, false);
    static final Mod TRACERS = new Mod("Tracers", "Lines + arrows", Cat.RENDER, true, false);
    static final Mod BLOCKESP = new Mod("Block ESP", "Spawner/Ore glow", Cat.RENDER, false, false);

    // --- MISC ---
    static final Mod AUTOLOGIN = new Mod("Auto Login", "/login cmd", Cat.MISC, false, true);
    static final Mod AUTOREGISTER = new Mod("Auto Register", "/register cmd", Cat.MISC, false, true);
    static final Mod AUTODISCONNECT = new Mod("Auto Disconnect", "If staff near", Cat.MISC, false, false);

    // --- HUD ---
    static final Mod WATERMARK = new Mod("Watermark", "Client name", Cat.HUD, true, false);
    static final Mod ARRAYLIST = new Mod("ArrayList", "Active modules", Cat.HUD, true, false);
    static final Mod PING_HUD = new Mod("Ping", "Show ping", Cat.HUD, true, false);
    static final Mod FPS_HUD = new Mod("FPS", "Show FPS", Cat.HUD, true, false);

    // --- SETTINGS ---
    static final Num AURA_RANGE = new Num("Aura Range", "Attack distance", Cat.COMBAT, 4.5, 3.0, 6.0, 0.5);
    static final Num AIM_SPEED = new Num("Aim Speed", "1-100", Cat.COMBAT, 35, 1, 100, 5);
    static final Num REACH = new Num("Reach", "Extended reach", Cat.COMBAT, 3.0, 3.0, 6.0, 0.5);
    static final Num AIM_MODE = new Num("Aim Mode", "0=Legit 1=Bal 2=Rage", Cat.COMBAT, 1, 0, 2, 1);
    static final Num STORAGE_CHUNKS = new Num("Storage Chunks", "Scan radius", Cat.SMP, 32, 8, 32, 1);

    private static KeyBinding guiKey;
    static Cat lastTab = Cat.MACE;
    private static int totemCooldown = 0, armorCooldown = 0, potCooldown = 0;
    private static int auraCooldown = 0, trapCooldown = 0, stepCooldown = 0, kitCooldown = 0, chestCooldown = 0;
    private static int afkTimer = 0;
    private static float lastHealth = 20f;
    private static int lastDamageTime = -10, tickCounter = 0;

    // Mace State
    private enum MaceState { IDLE, EQUIP, THROW_PEARL, THROW_WIND, GLIDE, SMASH, COMBO }
    private static MaceState maceState = MaceState.IDLE;
    private static int maceTimer = 0, maceStateTicks = 0;
    private static PlayerEntity maceTarget;
    private static boolean elytraEquipped = false;

    // Storage
    private static final List<BlockPos> storageResults = new ArrayList<>();
    private static int storageShowTimer = 0;

    @Override
    public void onInitializeClient() {
        load();
        
        // Auto Resource Pack Hook - assets/minecraft/ in mod auto-loads
        try {
            ResourceManagerHelper.registerBuiltinResourcePack(
                Identifier.of("kingerlol", "kinger_pack"),
                FabricLoader.getInstance().getModContainer("kingerlol").orElseThrow(),
                Text.literal("Kinger Pack"),
                ResourcePackActivationType.ALWAYS_ENABLED
            );
        } catch (Exception ignored) {
            // Fallback: assets still load even without explicit registration
        }

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("kingerlol", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "KingerLol Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_5, category));

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

        float hp = p.getHealth();
        boolean tookDamage = hp < lastHealth - 0.1f;
        if (tookDamage && hp > 0) lastDamageTime = tickCounter;

        // Auto Disconnect
        if (AUTODISCONNECT.on && mc.getNetworkHandler() != null && tickCounter % 40 == 0) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String name = e.getProfile().getName().toLowerCase();
                if (name.contains("staff") || name.contains("admin") || name.contains("owner")) {
                    if (!e.getProfile().getId().equals(p.getUuid())) {
                        p.networkHandler.getConnection().disconnect(Text.literal("§cStaff detected"));
                        return;
                    }
                }
            }
        }

        // Anti-AFK
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

        if (SPRINT.on && mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.isUsingItem()) mc.options.sprintKey.setPressed(true);
        if (NOSLOW.on) noSlow(p);
        if (AUTOSHIELD.on) autoShield(mc, p);
        if (NOFALL.on) noFall(mc, p);
        if (STEP.on) step(mc, p);
        if (SCAFFOLD.on) scaffold(mc, p);
        if (BUNNYHOP.on && mc.options.forwardKey.isPressed() && p.isOnGround()) p.jump();

        if (FULLBRIGHT.on) fullbright(p);
        if (PLAYERESP.on) playerEsp(mc);

        if (AUTOSWORD.on) autoSword(mc, p);
        if (BOWAIMBOT.on) bowAimbot(mc, p);
        if (AIMASSIST.on) aimAssist(mc, p);
        if (!MACE_KILLER.on) killAura(mc, p);

        if (VELOCITY.on) {
            int ping = getPing(mc);
            float reduce = ping > 100 ? 0.3f : 0.4f;
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

    // ============ STORAGE FINDER ============

    static void findStorage(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        storageResults.clear();
        ChunkPos center = p.getChunkPos();
        int radius = STORAGE_CHUNKS.value;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = mc.world.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity) storageResults.add(pos.toImmutable());
                }
            }
        }
        BlockPos me = p.getBlockPos();
        storageResults.sort(Comparator.comparingDouble(b -> b.getSquaredDistance(me)));

        p.sendMessage(Text.literal("§b═══ STORAGE v500Q ═══"), false);
        p.sendMessage(Text.literal("§fFound §b" + storageResults.size() + "§f chests in §b" + radius + "§f chunks"), false);
        int n = Math.min(15, storageResults.size());
        for (int i = 0; i < n; i++) {
            BlockPos b = storageResults.get(i);
            int dist = (int) Math.sqrt(b.getSquaredDistance(me));
            p.sendMessage(Text.literal("§e" + (i+1) + ". §f" + b.getX() + " " + b.getY() + " " + b.getZ() + " §7(" + dist + "m)"), false);
        }
        storageShowTimer = 20 * 30;
    }

    // ============ CHEST STEALER ============

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

    // ============ MACE KILLER ============

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

    private static void maceKiller(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        switch (maceState) {
            case IDLE -> {
                if (!isKitReady(p)) { maceState = MaceState.EQUIP; return; }
                PlayerEntity target = null;
                double bestD = MACE_RANGE.value * MACE_RANGE.value;
                for (PlayerEntity o : mc.world.getPlayers()) {
                    if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
                    double d = p.squaredDistanceTo(o);
                    if (d < bestD) { bestD = d; target = o; }
                }
                if (target == null) return;
                maceTarget = target;
                maceState = MaceState.THROW_PEARL; maceTimer = 0; maceStateTicks = 0;
                lookAt(p, maceTarget.getEyePos());
                p.getInventory().setSelectedSlot(2);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
            }
            case EQUIP -> {
                if (kitCooldown > 0) { kitCooldown--; return; }
                int mS = findItemSlot(p, Items.MACE), wS = findItemSlot(p, Items.WIND_CHARGE);
                int pS = findItemSlot(p, Items.ENDER_PEARL), eS = findItemSlot(p, Items.ELYTRA);
                if (mS == -1 || wS == -1 || pS == -1 || eS == -1) {
                    p.sendMessage(Text.literal("§cMissing Mace/Wind/Pearl/Elytra!"), true);
                    MACE_KILLER.on = false; maceState = MaceState.IDLE; return;
                }
                int sync = p.playerScreenHandler.syncId;
                if (mS != 0) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(mS), 0, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (wS != 1) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(wS), 1, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (pS != 2) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(pS), 2, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (eS != 3) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(eS), 3, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                maceState = MaceState.IDLE;
            }
            case THROW_PEARL -> {
                maceTimer++;
                if (maceTimer >= (int) PEARL_DELAY.value) {
                    p.getInventory().setSelectedSlot(1);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.THROW_WIND; maceTimer = 0;
                }
                if (maceStateTicks++ > 30) resetMace(mc, p);
            }
            case THROW_WIND -> {
                maceTimer++;
                if (maceTimer >= 3) {
                    if (!elytraEquipped) { swapChest(mc, p); elytraEquipped = true; }
                    if (!p.isGliding() && !p.isOnGround()) {
                        p.startGliding();
                        p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    }
                    maceState = MaceState.GLIDE; maceStateTicks = 0;
                }
            }
            case GLIDE -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;
                lookAt(p, maceTarget.getEyePos());
                boolean above = p.getY() > maceTarget.getY() + 2.0;
                double dist = p.distanceTo(maceTarget);
                if ((above && (dist < 8.0 || p.getVelocity().y < 0)) || dist < 4.0) {
                    if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
                    maceState = MaceState.SMASH; maceTimer = 2; maceStateTicks = 0;
                } else if (p.isOnGround() || maceStateTicks > 100) resetMace(mc, p);
            }
            case SMASH -> {
                if (maceTimer > 0) { maceTimer--; return; }
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                p.getInventory().setSelectedSlot(0);
                lookAt(p, maceTarget.getEyePos());
                if (p.distanceTo(maceTarget) <= 3.5 && p.getAttackCooldownProgress(0f) >= 0.9f) {
                    mc.interactionManager.attackEntity(p, maceTarget);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.COMBO; maceStateTicks = 0;
                } else if (p.isOnGround()) resetMace(mc, p);
            }
            case COMBO -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;
                if (maceStateTicks < 20) {
                    p.getInventory().setSelectedSlot(0);
                    lookAt(p, maceTarget.getEyePos());
                    if (p.distanceTo(maceTarget) <= 4.0 && p.getAttackCooldownProgress(0f) >= 0.9f) {
                        mc.interactionManager.attackEntity(p, maceTarget);
                        p.swingHand(Hand.MAIN_HAND);
                    }
                } else resetMace(mc, p);
            }
        }
    }

    private static void resetMace(MinecraftClient mc, ClientPlayerEntity p) {
        if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
        maceState = MaceState.IDLE; maceTarget = null; maceTimer = 0; maceStateTicks = 0;
    }

    private static void swapChest(MinecraftClient mc, ClientPlayerEntity p) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, 6, 3, SlotActionType.SWAP, p);
    }

    // ============ COMBAT ============

    private static boolean isBot(PlayerEntity player, MinecraftClient mc) {
        if (!ANTIBOT.on || mc.getNetworkHandler() == null) return false;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null;
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

        // Shield breaker
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
                auraCooldown = 2;
                return;
            }
        }

        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting() && !p.isTouchingWater()) {
            p.jump(); auraCooldown = 2; return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d pos = target.getEyePos();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float rotYaw = (float) (Math.round((p.getYaw() + wrapDegrees(targetYaw - p.getYaw())) / sens) * sens);
        float rotPitch = (float) (Math.round((p.getPitch() + (targetPitch - p.getPitch())) / sens) * sens);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotYaw, rotPitch, p.isOnGround(), p.horizontalCollision));
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        int ping = getPing(mc);
        auraCooldown = Math.max(1, (ping / 50) + 1);
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
        float speedF = Math.min(1f, AIM_SPEED.value / 100f);
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float finalYaw = p.getYaw() + wrapDegrees(wantYaw - p.getYaw()) * speedF;
        float finalPitch = p.getPitch() + (wantPitch - p.getPitch()) * speedF;
        finalYaw += (float) ((Math.random() - 0.5) * 0.05);
        finalPitch += (float) ((Math.random() - 0.5) * 0.05);
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

    // ============ CRYSTAL ============

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
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(pos);
                    if ((st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK)) && mc.world.isAir(pos.up())) {
                        if (p.squaredDistanceTo(pos.toCenterPos()) <= 25) return pos;
                    }
                }
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
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                        if (p.squaredDistanceTo(pos.toCenterPos()) <= 36) return pos;
                    }
                }
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
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.OBSIDIAN)) { blockSlot = i; break; }
        }
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

    // ============ GENERAL ============

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
        // Fallback to enchanted golden apple
        if (p.getHealth() < 8) {
            for (int slot = 9; slot <= 44; slot++) {
                if (p.playerScreenHandler.getSlot(slot).getStack().isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                    mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                    totemCooldown = cd;
                    return;
                }
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

    private static void scaffold(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        boolean shouldPlace = !p.isOnGround() && p.getVelocity().y < 0;
        boolean tower = mc.options.jumpKey.isPressed() && p.isOnGround();
        if (!shouldPlace && !tower) return;
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
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    private static void noFall(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.fallDistance > 2.0f) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            p.fallDistance = 0f;
        }
    }

    // ============ RENDER ============

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

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden || mc.currentScreen instanceof Menu) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (TRACERS.on) {
            Vec3d camPos = mc.gameRenderer.getCamera().getPos();
            float camYaw = mc.gameRenderer.getCamera().getYaw();
            float camPitch = mc.gameRenderer.getCamera().getPitch();
            double fov = mc.options.getFov().getValue();
            double focal = (sh / 2.0) / Math.tan(Math.toRadians(fov / 2.0));
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || isBot(o, mc)) continue;
                double d = p.distanceTo(o);
                if (d > 96) continue;
                int[] sc = projectToScreen(new Vec3d(o.getX(), o.getY() + o.getHeight() / 2.0, o.getZ()),
                        camPos, camYaw, camPitch, sw, sh, focal);
                if (sc == null) continue;
                ctx.fill(sw / 2, sh - 1, sc[0], sh + 1, 0xFF00E5FF);
                Vec3d oVel = o.getVelocity();
                if (oVel.horizontalLength() > 0.05) {
                    double angle = Math.toDegrees(Math.atan2(oVel.z, oVel.x));
                    int ax = (int)(sc[0] + 15 * Math.cos(Math.toRadians(angle)));
                    int ay = (int)(sc[1] + 15 * Math.sin(Math.toRadians(angle)));
                    ctx.fill(sc[0], sc[1] - 1, ax, ay, 0xFFFF5555);
                }
            }
        }

        if (storageShowTimer > 0 && !storageResults.isEmpty()) {
            int panelY = sh / 4;
            int panelX = sw / 2 - 120;
            int listH = 20 * Math.min(15, storageResults.size()) + 25;
            ctx.fill(panelX - 5, panelY - 5, panelX + 245, panelY + listH, 0xDD000000);
            ctx.fill(panelX - 5, panelY - 5, panelX - 3, panelY + listH, 0xFF00E5FF);
            ctx.drawTextWithShadow(tr, "§bSTORAGE v500Q §7(" + storageResults.size() + ")", panelX, panelY, 0xFFFFFFFF);
            int n = Math.min(15, storageResults.size());
            for (int i = 0; i < n; i++) {
                BlockPos b = storageResults.get(i);
                int dist = (int) Math.sqrt(b.getSquaredDistance(p.getBlockPos()));
                ctx.drawTextWithShadow(tr, "§e" + (i+1) + ". §f" + b.getX() + " " + b.getY() + " " + b.getZ() + " §7(" + dist + "m)",
                        panelX, panelY + 15 + i * 12, 0xFFFFFFFF);
            }
        }

        int y = 4;
        if (WATERMARK.on) {
            long t = System.currentTimeMillis() / 100;
            int r = (int)(Math.sin(t * 0.05) * 80 + 175);
            int g = (int)(Math.sin(t * 0.05 + 2) * 80 + 175);
            int b = (int)(Math.sin(t * 0.05 + 4) * 80 + 175);
            ctx.drawTextWithShadow(tr, NAME + " §7" + VERSION, 4, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            y += 14;
        }
        if (PING_HUD.on) {
            PlayerListEntry e = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) : null;
            int ping = e != null ? e.getLatency() : 0;
            ctx.drawTextWithShadow(tr, "§7Ping: §f" + ping + "ms", 4, y, 0xFFFFFFFF);
            y += 11;
        }
        if (FPS_HUD.on) {
            ctx.drawTextWithShadow(tr, "§7FPS: §f" + mc.getCurrentFps(), 4, y, 0xFFFFFFFF);
            y += 11;
        }
        if (ARRAYLIST.on) {
            List<Mod> active = new ArrayList<>();
            for (Mod m : MODS) if (m.on && !m.action && m.cat != Cat.HUD) active.add(m);
            active.sort(Comparator.comparingInt((Mod m) -> -tr.getWidth(m.name)));
            int ay = 4;
            for (Mod m : active) {
                long t = System.currentTimeMillis() / 100;
                int a_r = (int)(Math.sin(t * 0.05 + ay * 0.1) * 80 + 175);
                int a_g = (int)(Math.sin(t * 0.05 + 2 + ay * 0.1) * 80 + 175);
                int a_b = (int)(Math.sin(t * 0.05 + 4 + ay * 0.1) * 80 + 175);
                int col = 0xFF000000 | (a_r << 16) | (a_g << 8) | a_b;
                ctx.drawTextWithShadow(tr, m.name, sw - tr.getWidth(m.name) - 4, ay, col);
                ay += 11;
            }
        }
    }

    // ============ MENU ============

    enum Kind { TAB, TOGGLE, ACTION, STEP, VALUE }
    record Widget(int x, int y, int w, int h, String label, Kind kind, BooleanSupplier active, String desc, Supplier<String> tag) {}

    static class Menu extends Screen {
        static final int PW = 520, PH = 360;
        private final List<Widget> widgets = new ArrayList<>();
        final int BG_COLOR = 0xF0101018;
        final int PANEL_COLOR = 0xF01E1E28;
        final int ACCENT = 0xFF00E5FF;
        final int TEXT_COLOR = 0xFFEEEEEE;
        final int TEXT_DARK = 0xFF666677;
        private final long openTime = System.currentTimeMillis();

        Menu() { super(Text.literal(NAME)); }
        private int px() { return (this.width - PW) / 2; }
        private int py() { return (this.height - PH) / 2; }

        private void hit(int x, int y, int w, int h, Runnable action) {
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> action.run()).dimensions(x, y, w, h).build());
        }

        @Override protected void init() {
            widgets.clear();
            int px = px(), py = py();
            int sx = px + 10, sy = py + 45, i = 0;
            for (Cat c : Cat.values()) {
                final Cat cc = c; int ty = sy + i * 26;
                hit(sx, ty, 115, 24, () -> { lastTab = cc; save(); clearAndInit(); });
                widgets.add(new Widget(sx, ty, 115, 24, c.label, Kind.TAB, () -> lastTab == cc, null, null));
                i++;
            }
            int cx = px + 135, cy = py + 45, col = 0, row = 0;
            for (Mod m : MODS) {
                if (m.cat != lastTab) continue;
                int mx = cx + col * 185, my = cy + row * 28;
                hit(mx, my, 180, 24, () -> {
                    if (m.action) {
                        if (m == STORAGE) { close(); findStorage(this.client); }
                        else if (m == AUTOLOGIN) { close(); autoLogin(this.client); }
                        else if (m == AUTOREGISTER) { close(); autoRegister(this.client); }
                    } else { m.on = !m.on; save(); }
                });
                widgets.add(new Widget(mx, my, 180, 24, m.name, m.action ? Kind.ACTION : Kind.TOGGLE, () -> m.on, m.desc, null));
                col++; if (col == 2) { col = 0; row++; }
            }
            if (col == 1) row++;
            for (Num n : NUMS) {
                if (n.cat != lastTab) continue;
                int ny = cy + row * 30;
                widgets.add(new Widget(cx, ny, 260, 22, n.name, Kind.VALUE, () -> false, n.desc, () -> String.valueOf(n.value)));
                hit(cx + 265, ny, 22, 22, () -> { n.change(-1); save(); });
                widgets.add(new Widget(cx + 265, ny, 22, 22, "-", Kind.STEP, () -> false, null, null));
                hit(cx + 290, ny, 22, 22, () -> { n.change(1); save(); });
                widgets.add(new Widget(cx + 290, ny, 22, 22, "+", Kind.STEP, () -> false, null, null));
                row++;
            }
        }

        @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
            ctx.fill(0, 0, this.width, this.height, ((int)(0x99 * t)) << 24);
            int px = px(), py = py();
            for (int i = 0; i < 4; i++) {
                int alpha = (int)(0x50 * t) >> i;
                ctx.fill(px - 3 - i, py - 3 - i, px + PW + 3 + i, py + PH + 3 + i, (alpha << 24) | 0x00E5FF);
            }
            ctx.fill(px, py, px + PW, py + PH, BG_COLOR);
            ctx.fill(px, py, px + 135, py + PH, PANEL_COLOR);
            ctx.fill(px, py, px + PW, py + 35, 0xF0080810);
            ctx.fill(px, py + 35, px + PW, py + 36, ACCENT);
            int scanY = py + 35 + (int)((System.currentTimeMillis() / 5) % PH);
            if (scanY < py + PH) ctx.fill(px, scanY, px + PW, scanY + 1, 0x2000E5FF);
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            TextRenderer tr = this.textRenderer;
            int px = px(), py = py();
            long t = System.currentTimeMillis() / 100;
            int r = (int)(Math.sin(t * 0.05) * 80 + 175);
            int g = (int)(Math.sin(t * 0.05 + 2) * 80 + 175);
            int b = (int)(Math.sin(t * 0.05 + 4) * 80 + 175);
            ctx.drawTextWithShadow(tr, NAME, px + 12, py + 12, 0xFF000000 | (r << 16) | (g << 8) | b);
            ctx.drawTextWithShadow(tr, VERSION, px + PW - tr.getWidth(VERSION) - 12, py + 12, TEXT_DARK);
            for (Widget w : widgets) {
                boolean hov = mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() && mouseY < w.y() + w.h();
                boolean on = w.active().getAsBoolean();
                if (w.kind() == Kind.TAB) {
                    if (on) {
                        ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), 0xFF2A2A38);
                        ctx.fill(w.x(), w.y(), w.x() + 3, w.y() + w.h(), ACCENT);
                    } else if (hov) {
                        ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), 0xFF2A2A38);
                    }
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 10, w.y() + (w.h() - 8) / 2, on ? ACCENT : TEXT_COLOR);
                } else if (w.kind() == Kind.TOGGLE) {
                    int bgColor = on ? 0xFF0088AA : 0xFF333340;
                    if (hov) bgColor = on ? ACCENT : 0xFF444455;
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 5, w.y() + (w.h() - 8) / 2, on ? 0xFFFFFFFF : TEXT_DARK);
                    int toggleX = w.x() + w.w() - 32;
                    int toggleY = w.y() + 6;
                    ctx.fill(toggleX, toggleY, toggleX + 24, toggleY + 12, bgColor);
                    int circleX = on ? toggleX + 14 : toggleX + 2;
                    ctx.fill(circleX, toggleY + 2, circleX + 8, toggleY + 10, 0xFFFFFFFF);
                } else if (w.kind() == Kind.VALUE) {
                    ctx.drawTextWithShadow(tr, w.label(), w.x(), w.y() + 7, TEXT_COLOR);
                    ctx.drawTextWithShadow(tr, w.tag().get(), w.x() + w.w() - tr.getWidth(w.tag().get()), w.y() + 7, ACCENT);
                } else if (w.kind() == Kind.STEP) {
                    int bg = hov ? 0xFF444455 : 0xFF222230;
                    ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                    int tw = tr.getWidth(w.label());
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + (w.w() - tw) / 2, w.y() + (w.h() - 8) / 2, TEXT_COLOR);
                } else if (w.kind() == Kind.ACTION) {
                    int bg = hov ? 0xFF2A2A38 : PANEL_COLOR;
                    ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 5, w.y() + (w.h() - 8) / 2, ACCENT);
                }
            }
        }
        @Override public boolean shouldPause() { return false; }
    }
        }
