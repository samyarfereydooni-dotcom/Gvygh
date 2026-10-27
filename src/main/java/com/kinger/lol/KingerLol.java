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
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
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
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public class KingerLol implements ClientModInitializer {

    public static final String NAME = "Kinger";
    public static final String VERSION = "vINFI";

    enum Cat {
        COMBAT("Combat"), MACE("Mace Killer"), CRYSTAL("Crystal"), BASE("Base Finder"),
        SMP("SMP"), MOVEMENT("Movement"), RENDER("Render"), MISC("Misc"),
        ADMIN("§6§lADMIN"), ACCESSORY("§d§lACCESSORY"), HUD("HUD");
        final String label;
        Cat(String label) { this.label = label; }
    }

    static final List<Mod> MODS = new ArrayList<>();
    static final List<Num> NUMS = new ArrayList<>();

    static final class Mod {
        final String name, desc; final Cat cat; final boolean action; boolean on;
        boolean vip = false;
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

    // ============ MODULES ============
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
    static final Mod MACE_CRIT_STYLES = new Mod("Mace Crit Styles", "100K visual crits", Cat.MACE, true, false);
    static final Mod MACE_CRIT_RANDOM = new Mod("Crit Random", "Random every attack", Cat.MACE, true, false);
    static final Num MACE_RANGE = new Num("Target Range", "Radius", Cat.MACE, 20.0, 5.0, 50.0, 1.0);
    static final Num MACE_CRIT_STYLE = new Num("Mace Crit Style", "0-99999", Cat.MACE, 0, 0, 99999, 1000);
    static final Num MACE_ANIM_SPEED = new Num("Anim Speed", "0.5=slow 3=fast", Cat.MACE, 1.5, 0.5, 3.0, 0.1);
    static final Num AIM_SMOOTH = new Num("Aim Smooth", "1=slow 100=instant", Cat.MACE, 20.0, 5.0, 100.0, 5.0);
    static final Num ROT_SPEED = new Num("Rotate Speed", "Deg/tick", Cat.MACE, 20.0, 5.0, 45.0, 1.0);

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
    static final Mod SPAWNER_PROTECT = new Mod("Spawner Protect", "Mine spawner", Cat.SMP, false, false);

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

    // ADMIN MODULES
    static final Mod ADMIN_KILLALL = new Mod("Kill All", "Attack every nearby player", Cat.ADMIN, false, false);
    static final Mod ADMIN_FLY = new Mod("Fly", "Infinite flight", Cat.ADMIN, false, false);
    static final Mod ADMIN_XRAY = new Mod("X-Ray", "See through walls", Cat.ADMIN, false, false);
    static final Mod ADMIN_REACH = new Mod("Infinite Reach", "10 block reach", Cat.ADMIN, false, false);
    static final Mod ADMIN_SPEED = new Mod("Speed Hack", "10x speed", Cat.ADMIN, false, false);
    static final Mod ADMIN_TOTEM = new Mod("Fast Totem", "Instant swap", Cat.ADMIN, false, false);
    static final Mod GOD_CROWN = new Mod("§6§l👑 God Crown", "Crown with horns", Cat.ADMIN, false, false);
    static final Mod GOD_WINGS = new Mod("§c§l🔥 Fire Wings", "Godlike fire wings", Cat.ADMIN, false, false);
    static final Mod GOD_AURA = new Mod("§6§l✨ Admin Aura", "Golden aura", Cat.ADMIN, false, false);
    static final Mod ADMIN_ALLON = new Mod("ALL ON", "Enable everything", Cat.ADMIN, false, true);
    static final Mod ADMIN_ALLOFF = new Mod("ALL OFF", "Disable everything", Cat.ADMIN, false, true);
    static final Mod ADMIN_RELOAD = new Mod("Reload Config", "Reload from disk", Cat.ADMIN, false, true);
    static final Mod ADMIN_SAVE = new Mod("Save Config", "Save to disk", Cat.ADMIN, false, true);
    static final Mod ADMIN_GENS = new Mod("Generate Code", "Create new VIP code", Cat.ADMIN, false, true);
    static final Mod ADMIN_CLEARCODES = new Mod("Clear Custom", "Remove custom codes", Cat.ADMIN, false, true);

    static final Num ADMIN_SPEED_MULT = new Num("Speed Mult", "Speed multiplier", Cat.ADMIN, 10.0, 1.0, 20.0, 1.0);
    static final Num ADMIN_FLY_SPEED = new Num("Fly Speed", "Vertical speed", Cat.ADMIN, 1.5, 0.5, 5.0, 0.1);

    // ACCESSORIES
    static final Mod ACCESSORY_TAB_ON = new Mod("Accessories ON", "Enable accessories", Cat.ACCESSORY, false, false);
    static final Mod ACCESSORY_KILL_EFFECT = new Mod("Kill Effects", "On kill", Cat.ACCESSORY, true, false);
    static final Mod ACCESSORY_HIT_EFFECT = new Mod("Hit Effects", "On hit", Cat.ACCESSORY, true, false);
    static final Mod ACCESSORY_MOVE_EFFECT = new Mod("Move Effects", "Trail", Cat.ACCESSORY, false, false);
    static final Mod ACCESSORY_SOUND = new Mod("Sound Effects", "Play sound", Cat.ACCESSORY, true, false);
    static final Mod ACCESSORY_ENABLED = new Mod("Enable Accessory", "Use selected", Cat.ACCESSORY, true, false);
    static final Mod ACCESSORY_RANDOM = new Mod("Random Accessory", "Random trigger", Cat.ACCESSORY, false, false);
    static final Num ACCESSORY_ID = new Num("Accessory ID", "Tier-based", Cat.ACCESSORY, 0, 0, 10000000000.0, 100.0);
    static final Num ACCESSORY_INTENSITY = new Num("Intensity", "1-10 particles", Cat.ACCESSORY, 3.0, 1.0, 10.0, 1.0);

    static final Mod WATERMARK = new Mod("Watermark", "Client name", Cat.HUD, true, false);
    static final Mod ARRAYLIST = new Mod("ArrayList", "Active", Cat.HUD, true, false);
    static final Mod PING_HUD = new Mod("Ping", "Ping", Cat.HUD, true, false);
    static final Mod FPS_HUD = new Mod("FPS", "FPS", Cat.HUD, true, false);

    static final Num AURA_RANGE = new Num("Aura Range", "Attack range", Cat.COMBAT, 3.1, 3.0, 6.0, 0.1);
    static final Num AIM_SPEED = new Num("Aim Speed", "1-100", Cat.COMBAT, 40, 1, 100, 5);
    static final Num REACH = new Num("Reach", "Reach", Cat.COMBAT, 3.0, 3.0, 3.2, 0.1);
    static final Num AIM_MODE = new Num("Aim Mode", "0=L 1=B 2=R", Cat.COMBAT, 0, 0, 2, 1);

    static {
        MACE_KILLER.vip = true;
        MACE_CRIT_STYLES.vip = true;
        MACE_CRIT_RANDOM.vip = true;
        AUTOCRYSTAL.vip = true;
        ANCHOR_AURA.vip = true;
        AUTOTRAP.vip = true;
        SPAWNER_PROTECT.vip = true;
    }

    // AUTH
    static boolean authenticated = false;
    static boolean authenticatedVip = false;
    static boolean adminAuthenticated = false;
    static final String[] VALID_CODES = { "am not noob" };
    static final String[] VIP_CODES = { "I not noob vip is here" };
    static final String[] ADMIN_CODES = { "Saberx_x boses" };
    static final List<String> customUserCodes = new ArrayList<>();
    static final List<String> customVipCodes = new ArrayList<>();
    static final List<String> customAdminCodes = new ArrayList<>();

    // ACCESSORY SYSTEM
    enum AccessoryType {
        TOTEM_POP("Totem Pop"), CRIT_STARS("Crit Stars"), KILL_BURST("Kill Burst"),
        MOVE_TRAIL("Move Trail"), HIT_SPARK("Hit Spark"), LAND_PUFF("Land Puff"),
        JUMP_LEAP("Jump Leap"), DAMAGE_FIRE("Damage Fire"), HIT_MARK("Hit Mark"),
        RING("Ring"), SPIRAL("Spiral"), FIREWORK("Firework"),
        LIGHTNING("Lightning"), HEART("Heart"), FLAME("Flame"),
        SPARKLE("Sparkle"), SOUL("Soul"), ENCHANT("Enchant"),
        PORTAL("Portal"), SMOKE("Smoke"), BUBBLE("Bubble"),
        NOTE("Note"), COMBO_TEXT("Combo Text");
        final String label;
        AccessoryType(String label) { this.label = label; }
    }

    enum AccessoryTier { NORMAL, VIP, ADMIN }

    static class Accessory {
        final long id;
        final AccessoryType type;
        final int colorHex;
        final int phase, sizeMod, speedMod, pattern;
        final String name;
        Accessory(long id, AccessoryType type, int colorHex, int phase, int sizeMod, int speedMod, int pattern) {
            this.id = id; this.type = type; this.colorHex = colorHex;
            this.phase = phase; this.sizeMod = sizeMod; this.speedMod = speedMod; this.pattern = pattern;
            String hex = String.format("#%06X", colorHex & 0xFFFFFF);
            this.name = type.label + " " + hex;
        }
    }

    static AccessoryTier getTier() {
        if (adminAuthenticated) return AccessoryTier.ADMIN;
        if (authenticatedVip) return AccessoryTier.VIP;
        return AccessoryTier.NORMAL;
    }

    static long getMaxAccessoryId() {
        return switch (getTier()) {
            case ADMIN -> 10_000_000_000L;
            case VIP -> 1_000_000L;
            case NORMAL -> 2_000L;
        };
    }

    static String formatTierLabel() {
        return switch (getTier()) {
            case ADMIN -> "§6👑 ADMIN (10B)";
            case VIP -> "§6★ VIP (1M)";
            case NORMAL -> "§aNormal (2K)";
        };
    }

    static int hsvToRgb(float h, float s, float v) {
        float hh = h * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (((i % 6) + 6) % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    static Accessory generateAccessory(long id) {
        long max = getMaxAccessoryId();
        id = Math.max(0, Math.min(max - 1, id));
        int typeIdx = (int)(id % 23);
        int colorIdx = (int)((id / 23) % 31);
        int phase = (int)((id / (23 * 31)) % 100);
        int sizeMod = (int)((id / (23 * 31 * 100)) % 100);
        int speedMod = (int)((id / (23 * 31 * 100 * 100)) % 100);
        int pattern = (int)((id / (23 * 31 * 100 * 100 * 100)) % 50);
        AccessoryType type = AccessoryType.values()[typeIdx];
        int color = hsvToRgb(colorIdx / 31f, 0.85f, 1.0f);
        return new Accessory(id, type, color, phase, sizeMod, speedMod, pattern);
    }

    static Accessory currentAccessory = null;
    static int accessoryCooldown = 0;
    static int crownTick = 0, wingsTick = 0, auraTick = 0;

    // KEYS
    private static KeyBinding guiKey, autoCmdKey, logoutKey;
    static String autoCommandText = "/sell hand";
    static Cat lastTab = Cat.MACE;
    private static int totemCooldown = 0, armorCooldown = 0, potCooldown = 0, gappleCooldown = 0;
    private static int auraCooldown = 0, trapCooldown = 0, stepCooldown = 0, kitCooldown = 0, chestCooldown = 0;
    private static int afkTimer = 0, scaffoldTimer = 0, mineCooldown = 0, baseScanCooldown = 0;
    private static int wTapTimer = 0, spawnerCooldown = 0;
    private static float lastHealth = 20f;
    private static int lastDamageTick = -10, tickCounter = 0;

    private enum MaceState {
        IDLE, EQUIP, JUMP, ROTATE_DOWN, THROW_PEARL_DOWN, THROW_WIND_DOWN,
        WAIT_BOOST, EQUIP_ELYTRA, START_GLIDE, GLIDE, STOP_GLIDE, CRIT_ANIM, PRE_SMASH, FALL, COMBO
    }
    private static MaceState maceState = MaceState.IDLE;
    private static int maceTimer = 0, maceStateTicks = 0;
    private static PlayerEntity maceTarget;
    private static boolean elytraEquipped = false;
    private static double maceStartY = 0, fallStartY = 0;
    private static int attackAttempts = 0;
    private static float critAnimStartYaw = 0, critAnimStartPitch = 0;
    private static int currentAnimStyle = 0;

    private static final List<BlockPos> storageResults = new ArrayList<>();
    private static final List<BlockPos> chestCache = new ArrayList<>();
    private static int storageShowTimer = 0;

    @Override
    public void onInitializeClient() {
        load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("kingerlol", "main"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("KingerLol Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_5, category));
        autoCmdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Auto Command", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        logoutKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Kinger Logout", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("kingerlol").executes(ctx -> {
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new Menu()));
                return 1;
            }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(KingerLol::tick);
        HudRenderCallback.EVENT.register(KingerLol::renderHud);
    }

    private static void selectSlot(ClientPlayerEntity p, int slot) {
        p.getInventory().setSelectedSlot(slot);
        p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static Path configPath() { return FabricLoader.getInstance().getConfigDir().resolve("kingerlol.properties"); }

    static void save() {
        try {
            Properties pr = new Properties();
            for (Mod m : MODS) if (!m.action) pr.setProperty("mod." + m.name.replace(" ", "_").replace("§",""), String.valueOf(m.on));
            for (Num n : NUMS) pr.setProperty("num." + n.name.replace(" ", "_"), String.valueOf(n.value));
            pr.setProperty("autocmd", autoCommandText);
            pr.setProperty("authenticated", String.valueOf(authenticated));
            pr.setProperty("authenticatedVip", String.valueOf(authenticatedVip));
            pr.setProperty("adminAuthenticated", String.valueOf(adminAuthenticated));
            pr.setProperty("custom.user.count", String.valueOf(customUserCodes.size()));
            for (int i = 0; i < customUserCodes.size(); i++) pr.setProperty("custom.user." + i, customUserCodes.get(i));
            pr.setProperty("custom.vip.count", String.valueOf(customVipCodes.size()));
            for (int i = 0; i < customVipCodes.size(); i++) pr.setProperty("custom.vip." + i, customVipCodes.get(i));
            pr.setProperty("custom.admin.count", String.valueOf(customAdminCodes.size()));
            for (int i = 0; i < customAdminCodes.size(); i++) pr.setProperty("custom.admin." + i, customAdminCodes.get(i));
            try (OutputStream os = Files.newOutputStream(configPath())) { pr.store(os, NAME); }
        } catch (Exception ignored) {}
    }

    private static void load() {
        try {
            Path path = configPath(); if (!Files.exists(path)) return;
            Properties pr = new Properties();
            try (InputStream is = Files.newInputStream(path)) { pr.load(is); }
            for (Mod m : MODS) { String v = pr.getProperty("mod." + m.name.replace(" ", "_").replace("§","")); if (v != null) m.on = Boolean.parseBoolean(v); }
            for (Num n : NUMS) { String v = pr.getProperty("num." + n.name.replace(" ", "_")); if (v != null) n.value = Double.parseDouble(v.trim()); }
            String ac = pr.getProperty("autocmd"); if (ac != null) autoCommandText = ac;
            authenticated = Boolean.parseBoolean(pr.getProperty("authenticated", "false"));
            authenticatedVip = Boolean.parseBoolean(pr.getProperty("authenticatedVip", "false"));
            adminAuthenticated = Boolean.parseBoolean(pr.getProperty("adminAuthenticated", "false"));
            customUserCodes.clear(); customVipCodes.clear(); customAdminCodes.clear();
            int uc = Integer.parseInt(pr.getProperty("custom.user.count", "0"));
            for (int i = 0; i < uc; i++) { String c = pr.getProperty("custom.user." + i); if (c != null) customUserCodes.add(c); }
            int vc = Integer.parseInt(pr.getProperty("custom.vip.count", "0"));
            for (int i = 0; i < vc; i++) { String c = pr.getProperty("custom.vip." + i); if (c != null) customVipCodes.add(c); }
            int adc = Integer.parseInt(pr.getProperty("custom.admin.count", "0"));
            for (int i = 0; i < adc; i++) { String c = pr.getProperty("custom.admin." + i); if (c != null) customAdminCodes.add(c); }
            for (Mod m : MODS) if (m.vip && !authenticatedVip && !adminAuthenticated) m.on = false;
            for (Mod m : MODS) if (m.cat == Cat.ADMIN && !adminAuthenticated) m.on = false;
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

        while (logoutKey.wasPressed()) {
            if (authenticated) {
                authenticated = false; authenticatedVip = false; adminAuthenticated = false;
                for (Mod m : MODS) if (m.vip || m.cat == Cat.ADMIN) m.on = false;
                save();
                p.sendMessage(Text.literal("§c[Kinger] Logged out."), false);
            }
        }

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

        if (!authenticated) {
            while (guiKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new LoginScreen());
            }
            return;
        }

        while (guiKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new Menu());
        while (autoCmdKey.wasPressed()) {
            if (mc.currentScreen == null && !autoCommandText.isBlank()) mc.setScreen(new ChatScreen(autoCommandText, false));
        }

        float hp = p.getHealth();
        boolean tookDamage = hp < lastHealth - 0.1f;
        if (tookDamage && hp > 0) lastDamageTick = tickCounter;

        if (ANTIAFK.on && mc.currentScreen == null) {
            afkTimer++;
            if (afkTimer > 100 + new Random().nextInt(80) && p.isOnGround()) { p.jump(); afkTimer = 0; }
        }

        if (AUTOTOTEM.on) autoTotem(mc, p, tookDamage);
        if (AUTOARMOR.on) autoArmor(mc, p);
        if (AUTOPOT.on) autoPot(mc, p);
        if (AUTOGAPPLE.on) autoGapple(mc, p);
        if (AUTORESPAWN.on && mc.currentScreen instanceof DeathScreen) p.requestRespawn();
        if (CHESTSTEALER.on) chestStealer(mc, p);
        if (SPAWNER_PROTECT.on && authenticatedVip) spawnerProtect(mc, p);

        if (SPRINT.on && mc.currentScreen == null && mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.isUsingItem()) mc.options.sprintKey.setPressed(true);
        if (NOSLOW.on) noSlow(p);
        if (AUTOSHIELD.on) autoShield(mc, p);
        if (NOFALL.on) noFall(mc, p);
        if (STEP.on) step(mc, p);
        if (SCAFFOLD.on) scaffold(mc, p);
        if (BUNNYHOP.on && mc.currentScreen == null && mc.options.forwardKey.isPressed() && p.isOnGround()) p.jump();
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
            if (tickCounter - lastDamageTick <= 1 && p.isOnGround() && !p.isJumping()) p.jump();
        }
        lastHealth = hp;

        if (AUTOCRYSTAL.on && authenticatedVip) autoCrystal(mc, p);
        if (ANCHOR_AURA.on && authenticatedVip) anchorAura(mc, p);
        if (AUTOTRAP.on && authenticatedVip) autoTrap(mc, p);
        if (MACE_KILLER.on && authenticatedVip) maceKiller(mc, p);

        // Admin modules
        if (adminAuthenticated) {
            if (ADMIN_KILLALL.on) adminKillAll(mc, p);
            if (ADMIN_FLY.on) adminFly(mc, p);
            if (ADMIN_SPEED.on) adminSpeed(mc, p);
            if (ADMIN_TOTEM.on) adminFastTotem(mc, p);
            if (GOD_CROWN.on) renderGodCrown(mc, p);
            if (GOD_WINGS.on) renderFireWings(mc, p);
            if (GOD_AURA.on) renderGodAura(mc, p);
        }

        if (accessoryCooldown > 0) accessoryCooldown--;
        if (storageShowTimer > 0) storageShowTimer--;
    }

    // ============ GOD ACCESSORIES ============
    private static void renderGodCrown(MinecraftClient mc, ClientPlayerEntity p) {
        crownTick++;
        Vec3d headPos = new Vec3d(p.getX(), p.getY() + p.getHeight() - 0.3, p.getZ());
        float yawRad = (float)Math.toRadians(p.getYaw());

        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double angle = (i / (double)segments) * Math.PI * 2 + crownTick * 0.04;
            double localX = Math.cos(angle) * 0.35;
            double localZ = Math.sin(angle) * 0.35;
            double rotatedX = localX * Math.cos(yawRad) - localZ * Math.sin(yawRad);
            double rotatedZ = localX * Math.sin(yawRad) + localZ * Math.cos(yawRad);
            p.world.addParticle(ParticleTypes.FIREWORK, headPos.x + rotatedX, headPos.y, headPos.z + rotatedZ, 0, 0.005, 0);
        }
        if (crownTick % 2 == 0) {
            double pulse = 1.0 + Math.sin(crownTick * 0.15) * 0.15;
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * Math.PI * 2 - crownTick * 0.06;
                double localX = Math.cos(angle) * 0.2 * pulse;
                double localZ = Math.sin(angle) * 0.2 * pulse;
                double rotatedX = localX * Math.cos(yawRad) - localZ * Math.sin(yawRad);
                double rotatedZ = localX * Math.sin(yawRad) + localZ * Math.cos(yawRad);
                p.world.addParticle(ParticleTypes.END_ROD, headPos.x + rotatedX, headPos.y + 0.05, headPos.z + rotatedZ, 0, 0.01, 0);
            }
        }
        for (int horn = 0; horn < 2; horn++) {
            double side = horn == 0 ? 1 : -1;
            double localXBase = side * 0.18;
            for (int i = 0; i < 10; i++) {
                double t = i / 10.0;
                double outward = side * (0.18 + t * 0.25);
                double up = 0.05 + t * 0.75;
                double tiltForward = -0.1 + t * 0.3;
                double rotatedX = outward * Math.cos(yawRad) - tiltForward * Math.sin(yawRad);
                double rotatedZ = outward * Math.sin(yawRad) + tiltForward * Math.cos(yawRad);
                if (i < 6) {
                    p.world.addParticle(ParticleTypes.FLAME, headPos.x + rotatedX, headPos.y + up, headPos.z + rotatedZ, 0, 0.01, 0);
                } else {
                    p.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, headPos.x + rotatedX, headPos.y + up, headPos.z + rotatedZ, 0, 0.02, 0);
                }
                if (i == 9) {
                    for (int g = 0; g < 3; g++) {
                        p.world.addParticle(ParticleTypes.END_ROD,
                            headPos.x + rotatedX + (new Random().nextFloat() - 0.5) * 0.15,
                            headPos.y + up + 0.05,
                            headPos.z + rotatedZ + (new Random().nextFloat() - 0.5) * 0.15, 0, 0.02, 0);
                    }
                }
            }
        }
        if (crownTick % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = (i / 4.0) * Math.PI * 2 + crownTick * 0.08;
                double localX = Math.cos(angle) * 0.5;
                double localZ = Math.sin(angle) * 0.5;
                double yOffset = 0.15 + Math.sin(crownTick * 0.2 + i) * 0.1;
                double rotatedX = localX * Math.cos(yawRad) - localZ * Math.sin(yawRad);
                double rotatedZ = localX * Math.sin(yawRad) + localZ * Math.cos(yawRad);
                p.world.addParticle(ParticleTypes.END_ROD, headPos.x + rotatedX, headPos.y + yOffset, headPos.z + rotatedZ, 0, 0.005, 0);
            }
        }
    }

    private static void renderFireWings(MinecraftClient mc, ClientPlayerEntity p) {
        wingsTick++;
        Vec3d backPos = new Vec3d(p.getX(), p.getY() + 1.2, p.getZ());
        float yawRad = (float)Math.toRadians(p.getYaw());
        float flap = (float)Math.sin(wingsTick * 0.25) * 0.25f + 0.85f;
        for (int wing = 0; wing < 2; wing++) {
            double sideMult = wing == 0 ? -1 : 1;
            for (int i = 0; i < 20; i++) {
                double t = i / 20.0;
                double side = sideMult * (0.2 + t * 1.8);
                double up = t * 0.3 + Math.sin(t * Math.PI) * 0.8 * flap;
                double forward = -0.2 - t * 0.5 + Math.sin(t * 2) * 0.15;
                double rotatedX = side * Math.cos(yawRad) - forward * Math.sin(yawRad);
                double rotatedZ = side * Math.sin(yawRad) + forward * Math.cos(yawRad);
                if (t < 0.5) {
                    p.world.addParticle(ParticleTypes.FLAME, backPos.x + rotatedX, backPos.y + up, backPos.z + rotatedZ, 0, 0.02, 0);
                } else if (t < 0.8) {
                    p.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, backPos.x + rotatedX, backPos.y + up, backPos.z + rotatedZ, 0, 0.02, 0);
                } else {
                    p.world.addParticle(ParticleTypes.END_ROD, backPos.x + rotatedX, backPos.y + up, backPos.z + rotatedZ, 0, 0.02, 0);
                }
                if (i % 3 == 0 && t < 0.7) {
                    p.world.addParticle(ParticleTypes.FIREWORK,
                        backPos.x + rotatedX + (new Random().nextFloat() - 0.5) * 0.15,
                        backPos.y + up + 0.1,
                        backPos.z + rotatedZ + (new Random().nextFloat() - 0.5) * 0.15, 0, 0.01, 0);
                }
            }
        }
        if (wingsTick % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                p.world.addParticle(ParticleTypes.END_ROD,
                    backPos.x + (new Random().nextFloat() - 0.5) * 2.5,
                    backPos.y + (new Random().nextFloat() - 0.5) * 1.5 - 0.3,
                    backPos.z + (new Random().nextFloat() - 0.5) * 2.5, 0, 0.03, 0);
            }
        }
    }

    private static void renderGodAura(MinecraftClient mc, ClientPlayerEntity p) {
        auraTick++;
        if (auraTick % 2 == 0) {
            for (int i = 0; i < 12; i++) {
                double angle = (i / 12.0) * Math.PI * 2 + auraTick * 0.1;
                double x = Math.cos(angle) * 0.9;
                double z = Math.sin(angle) * 0.9;
                double y = 0.3 + Math.sin(auraTick * 0.15 + i * 0.5) * 0.3;
                p.world.addParticle(ParticleTypes.END_ROD, p.getX() + x, p.getY() + y, p.getZ() + z, 0, 0.01, 0);
            }
        }
        if (auraTick % 4 == 0 && p.isOnGround()) {
            for (int i = 0; i < 8; i++) {
                double angle = (i / 8.0) * Math.PI * 2 + auraTick * 0.05;
                p.world.addParticle(ParticleTypes.FIREWORK,
                    p.getX() + Math.cos(angle) * 1.2, p.getY() + 0.05, p.getZ() + Math.sin(angle) * 1.2, 0, 0.01, 0);
            }
        }
        if (auraTick % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                p.world.addParticle(ParticleTypes.END_ROD,
                    p.getX() + (new Random().nextFloat() - 0.5) * 1.5,
                    p.getY() + new Random().nextFloat() * 2.0,
                    p.getZ() + (new Random().nextFloat() - 0.5) * 1.5, 0, 0.03, 0);
            }
        }
    }

    // ============ ADMIN TOGGLES ============
    private static void adminKillAll(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
            if (p.distanceTo(o) > 6.0) continue;
            lookAt(p, o.getEyePos());
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
            if (p.getAttackCooldownProgress(0f) >= 0.9f) {
                mc.interactionManager.attackEntity(p, o);
                p.swingHand(Hand.MAIN_HAND);
                spawnAccessoryEffect(mc, p, o.getPos(), "kill");
                return;
            }
        }
    }

    private static void adminFly(MinecraftClient mc, ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        double speed = ADMIN_FLY_SPEED.value;
        if (mc.options.jumpKey.isPressed()) p.setVelocity(v.x, speed, v.z);
        else if (mc.options.sneakKey.isPressed()) p.setVelocity(v.x, -speed, v.z);
        p.fallDistance = 0;
    }

    private static void adminSpeed(MinecraftClient mc, ClientPlayerEntity p) {
        if (!(mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
              || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())) return;
        Vec3d v = p.getVelocity();
        double mult = ADMIN_SPEED_MULT.value / 10.0;
        p.setVelocity(v.x * (1 + mult), v.y, v.z * (1 + mult));
    }

    private static void adminFastTotem(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.currentScreenHandler != p.playerScreenHandler) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        for (int slot = 9; slot <= 44; slot++) {
            if (p.playerScreenHandler.getSlot(slot).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                return;
            }
        }
    }

    private static void adminToggleAll(boolean state) {
        for (Mod m : MODS) {
            if (m.action) continue;
            if (m.cat == Cat.ADMIN) continue;
            if (m.vip && !authenticatedVip && !adminAuthenticated) continue;
            m.on = state;
        }
        save();
    }

    private static String adminGenerateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("VIP-");
        Random r = new Random();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
            if (i == 3 || i == 7) sb.append('-');
        }
        String code = sb.toString();
        customVipCodes.add(code);
        save();
        return code;
    }

    // ============ ACCESSORY EFFECTS ============
    private static void spawnAccessoryEffect(MinecraftClient mc, ClientPlayerEntity p, Vec3d pos, String eventType) {
        if (!ACCESSORY_ENABLED.on || p.world == null) return;
        if (!ACCESSORY_TAB_ON.on) return;
        if (accessoryCooldown > 0) return;
        boolean shouldFire = false;
        if (eventType.equals("kill") && ACCESSORY_KILL_EFFECT.on) shouldFire = true;
        if (eventType.equals("hit") && ACCESSORY_HIT_EFFECT.on) shouldFire = true;
        if (eventType.equals("move") && ACCESSORY_MOVE_EFFECT.on) shouldFire = true;
        if (eventType.equals("preview")) shouldFire = true;
        if (!shouldFire) return;
        Accessory acc;
        if (ACCESSORY_RANDOM.on) acc = generateAccessory((long)(new Random().nextDouble() * getMaxAccessoryId()));
        else acc = generateAccessory((long) ACCESSORY_ID.value);
        currentAccessory = acc;
        int baseCount = (int) ACCESSORY_INTENSITY.value;
        int count = baseCount + (acc.sizeMod / 20);
        float speedMult = 0.5f + (acc.speedMod / 100f) * 1.5f;
        float sizeMult = 0.5f + (acc.sizeMod / 100f) * 1.5f;
        spawnParticlesByType(p, acc, pos, count, speedMult, sizeMult);
        accessoryCooldown = 3 + (acc.phase % 5);
    }

    private static void spawnParticlesByType(ClientPlayerEntity p, Accessory acc, Vec3d pos, int count, float speedMult, float sizeMult) {
        Random rand = new Random();
        switch (acc.type) {
            case TOTEM_POP -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1, pos.z,
                        (rand.nextFloat() - 0.5) * speedMult, rand.nextFloat() * speedMult, (rand.nextFloat() - 0.5) * speedMult);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.ITEM_TOTEM_USE, 1f, 1f);
            }
            case CRIT_STARS -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.CRIT, pos.x, pos.y + 1, pos.z,
                        (rand.nextFloat() - 0.5) * speedMult, rand.nextFloat() * speedMult, (rand.nextFloat() - 0.5) * speedMult);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            }
            case KILL_BURST -> {
                for (int i = 0; i < count * 2; i++)
                    p.world.addParticle(ParticleTypes.EXPLOSION,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult,
                        pos.y + 1 + rand.nextFloat() * sizeMult,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0, 0);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 1f);
            }
            case MOVE_TRAIL -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.CLOUD,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 0.2,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.02, 0);
            }
            case HIT_SPARK -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z,
                        (rand.nextFloat() - 0.5) * speedMult, rand.nextFloat() * speedMult, (rand.nextFloat() - 0.5) * speedMult);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.5f);
            }
            case LAND_PUFF -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.CLOUD,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult * 2, pos.y,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult * 2, 0, 0.05, 0);
            }
            case JUMP_LEAP -> p.world.addParticle(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1, pos.z, 0, 0, 0);
            case DAMAGE_FIRE -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.FLAME,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 0.5,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.05, 0);
            }
            case HIT_MARK -> p.world.addParticle(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 1, pos.z, 0, 0, 0);
            case RING -> {
                for (int i = 0; i < count * 4; i++) {
                    double angle = (i / (double)(count * 4)) * Math.PI * 2;
                    double radius = sizeMult * 2;
                    p.world.addParticle(ParticleTypes.END_ROD,
                        pos.x + Math.cos(angle) * radius, pos.y + 0.5, pos.z + Math.sin(angle) * radius, 0, 0.05, 0);
                }
            }
            case SPIRAL -> {
                for (int i = 0; i < count * 4; i++) {
                    double angle = (i / (double)(count * 4)) * Math.PI * 4 + acc.phase * 0.1;
                    double radius = sizeMult * (0.5 + (i / (double)(count * 4)) * 1.5);
                    double y = pos.y + (i / (double)(count * 4)) * 2.0;
                    p.world.addParticle(ParticleTypes.END_ROD,
                        pos.x + Math.cos(angle) * radius, y, pos.z + Math.sin(angle) * radius, 0, 0.02, 0);
                }
            }
            case FIREWORK -> {
                for (int i = 0; i < count * 3; i++)
                    p.world.addParticle(ParticleTypes.FIREWORK, pos.x, pos.y + 1, pos.z,
                        (rand.nextFloat() - 0.5) * speedMult * 2, rand.nextFloat() * speedMult * 2, (rand.nextFloat() - 0.5) * speedMult * 2);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
            }
            case LIGHTNING -> {
                for (int i = 0; i < count * 2; i++)
                    p.world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult * 2,
                        pos.y + rand.nextFloat() * 3,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult * 2, 0, 0, 0);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.5f);
            }
            case HEART -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.HEART,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 1.5,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.1, 0);
            }
            case FLAME -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 0.5,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.05, 0);
            }
            case SPARKLE -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.END_ROD,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult * 2,
                        pos.y + rand.nextFloat() * 2,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult * 2, 0, 0.02, 0);
            }
            case SOUL -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.SOUL,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 0.5,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.1, 0);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.PARTICLE_SOUL_ESCAPE, 1f, 0.7f);
            }
            case ENCHANT -> {
                for (int i = 0; i < count * 2; i++)
                    p.world.addParticle(ParticleTypes.ENCHANT,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult * 2,
                        pos.y + rand.nextFloat() * 2,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult * 2, 0, 0.1, 0);
            }
            case PORTAL -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.PORTAL,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 1,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0, 0);
            }
            case SMOKE -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.LARGE_SMOKE,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 1,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.05, 0);
            }
            case BUBBLE -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.BUBBLE,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 1,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.1, 0);
            }
            case NOTE -> {
                for (int i = 0; i < count; i++)
                    p.world.addParticle(ParticleTypes.NOTE,
                        pos.x + (rand.nextFloat() - 0.5) * sizeMult, pos.y + 1.5,
                        pos.z + (rand.nextFloat() - 0.5) * sizeMult, 0, 0.1, 0);
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.5f, 1f);
            }
            case COMBO_TEXT -> {
                if (ACCESSORY_SOUND.on) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.5f, 2f);
            }
        }
    }

    // ============ MACE KILLER ============
    private static float applyCurve(int curveType, float t) {
        return switch (curveType % 10) {
            case 0 -> t;
            case 1 -> 1 - (1-t)*(1-t);
            case 2 -> t*t;
            case 3 -> t < 0.5f ? 2*t*t : 1 - (float)Math.pow(-2*t+2, 2)/2;
            case 4 -> (float)Math.sin(t * Math.PI / 2);
            case 5 -> (float)(1 - Math.cos(t * Math.PI / 2));
            case 6 -> {
                float n1 = 7.5625f, d1 = 2.75f;
                if (t < 1/d1) yield n1*t*t;
                if (t < 2/d1) yield n1*(t -= 1.5f/d1)*t + 0.75f;
                if (t < 2.5f/d1) yield n1*(t -= 2.25f/d1)*t + 0.9375f;
                yield n1*(t -= 2.625f/d1)*t + 0.984375f;
            }
            case 7 -> {
                if (t <= 0 || t >= 1) yield t;
                yield (float)(Math.pow(2, -10*t) * Math.sin((t*10 - 0.75) * (2*Math.PI/3)) + 1);
            }
            case 8 -> {
                float c1 = 1.70158f, c3 = c1 + 1;
                yield 1 + c3*(float)Math.pow(t-1, 3) + c1*(float)Math.pow(t-1, 2);
            }
            case 9 -> (float)(t * (1 + 0.3 * Math.sin(t * Math.PI * 4)));
            default -> t;
        };
    }

    private static float[] getCritAnimation(int style, int tick, int maxTick) {
        style = Math.max(0, Math.min(99999, style));
        int spinPattern = (style / 1000) % 100;
        int flipPattern = (style / 10) % 100;
        int modifier = style % 10;
        float t = Math.min(1f, (float)tick / maxTick);
        int spinAmountIdx = spinPattern / 10;
        int spinCurve = spinPattern % 10;
        float spinAmount = (spinAmountIdx - 5) * 216f;
        float spinDelta = spinAmount * applyCurve(spinCurve, t);
        int flipAmountIdx = flipPattern / 10;
        int flipCurve = flipPattern % 10;
        float flipAmount = (flipAmountIdx - 5) * 108f;
        float flipDelta = flipAmount * applyCurve(flipCurve, t);
        float yawJitter = 0, pitchJitter = 0;
        float modifierPhase = (float)Math.sin(tick * 1.2);
        switch (modifier) {
            case 1 -> yawJitter = modifierPhase * 8;
            case 2 -> pitchJitter = modifierPhase * 8;
            case 3 -> { yawJitter = (float)Math.sin(tick * 3) * 4; pitchJitter = (float)Math.cos(tick * 3) * 4; }
            case 4 -> { yawJitter = (float)Math.sin(tick * 2) * 12; pitchJitter = (float)Math.sin(tick * 2 + 1.5) * 12; }
            case 5 -> { if (t > 0.5f) { spinDelta *= 0.7f; flipDelta *= 0.7f; } }
            case 6 -> spinDelta *= 1 + 0.2f * (float)Math.sin(t * Math.PI * 3);
            case 7 -> { yawJitter = (new Random().nextFloat() - 0.5f) * 15; pitchJitter = (new Random().nextFloat() - 0.5f) * 15; }
            case 8 -> { yawJitter = (float)(Math.sin(tick * 5) * Math.cos(tick * 7)) * 10; pitchJitter = (float)(Math.cos(tick * 5) * Math.sin(tick * 7)) * 10; }
            case 9 -> { yawJitter = (new Random().nextFloat() - 0.5f) * 20; pitchJitter = (new Random().nextFloat() - 0.5f) * 20; spinDelta *= 1 + 0.3f * (new Random().nextFloat() - 0.5f); }
        }
        return new float[]{spinDelta + yawJitter, flipDelta + pitchJitter};
    }

    private static int getAnimTicks(int style) {
        style = Math.max(0, Math.min(99999, style));
        int spinPattern = (style / 1000) % 100;
        int flipPattern = (style / 10) % 100;
        int modifier = style % 10;
        int spinAmountIdx = spinPattern / 10;
        int flipAmountIdx = flipPattern / 10;
        int spinTicks = 3 + spinAmountIdx;
        int flipTicks = 3 + flipAmountIdx;
        int baseTicks = Math.max(spinTicks, flipTicks);
        if (modifier == 3 || modifier == 5) baseTicks += 2;
        if (modifier == 8) baseTicks += 3;
        if (modifier == 9) baseTicks += 4;
        return Math.max(2, (int)(baseTicks / MACE_ANIM_SPEED.value));
    }

    private static void maceKiller(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || p.isSpectator() || p.isDead()) return;
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
                maceStartY = p.getY();
                maceState = MaceState.JUMP;
                maceTimer = 0; maceStateTicks = 0;
                p.jump();
            }
            case EQUIP -> {
                if (kitCooldown > 0) { kitCooldown--; return; }
                int mS = findItemSlot(p, Items.MACE), wS = findItemSlot(p, Items.WIND_CHARGE);
                int pS = findItemSlot(p, Items.ENDER_PEARL), eS = findItemSlot(p, Items.ELYTRA);
                if (mS == -1 || wS == -1 || pS == -1 || eS == -1) {
                    p.sendMessage(Text.literal("§cMissing: Mace/Wind/Pearl/Elytra"), true);
                    MACE_KILLER.on = false; maceState = MaceState.IDLE; return;
                }
                int sync = p.playerScreenHandler.syncId;
                if (mS != 0) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(mS), 0, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (wS != 1) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(wS), 1, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (pS != 2) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(pS), 2, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                if (eS != 3) { mc.interactionManager.clickSlot(sync, invToHandlerSlot(eS), 3, SlotActionType.SWAP, p); kitCooldown = 3; return; }
                maceState = MaceState.IDLE;
            }
            case JUMP -> {
                maceTimer++;
                if (maceTimer >= 3 && !p.isOnGround()) { maceState = MaceState.ROTATE_DOWN; maceTimer = 0; }
                if (maceTimer > 20) resetMace(mc, p);
            }
            case ROTATE_DOWN -> {
                float current = p.getPitch();
                float diff = 85.0f - current;
                float step = Math.max(-(float)ROT_SPEED.value, Math.min((float)ROT_SPEED.value, diff));
                float newPitch = current + step;
                p.setPitch(newPitch);
                for (int i = 0; i < 3; i++) p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), newPitch, p.isOnGround(), p.horizontalCollision));
                maceTimer++;
                if (p.getPitch() >= 80.0f || maceTimer > 10) { maceState = MaceState.THROW_PEARL_DOWN; maceTimer = 0; }
            }
            case THROW_PEARL_DOWN -> {
                p.setPitch(85.0f);
                for (int i = 0; i < 3; i++) p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), 85.0f, p.isOnGround(), p.horizontalCollision));
                selectSlot(p, 2);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                maceState = MaceState.THROW_WIND_DOWN;
                maceTimer = 2;
            }
            case THROW_WIND_DOWN -> {
                maceTimer--;
                p.setPitch(85.0f);
                if (maceTimer <= 0) {
                    selectSlot(p, 1);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    maceState = MaceState.WAIT_BOOST;
                    maceTimer = 6; maceStateTicks = 0;
                }
            }
            case WAIT_BOOST -> {
                maceTimer--; maceStateTicks++;
                if (maceTimer <= 0) {
                    if (p.getVelocity().y > 0.1 || !p.isOnGround()) { maceState = MaceState.EQUIP_ELYTRA; maceTimer = 0; maceStateTicks = 0; }
                    else {
                        if (maceStateTicks > 15) resetMace(mc, p);
                        else { maceState = MaceState.THROW_WIND_DOWN; maceTimer = 2; }
                    }
                }
            }
            case EQUIP_ELYTRA -> {
                if (p.getY() < maceStartY + 3.0) { maceTimer++; if (maceTimer > 30) resetMace(mc, p); return; }
                if (!elytraEquipped) { selectSlot(p, 3); swapChest(mc, p); elytraEquipped = true; maceTimer = 2; return; }
                maceTimer--;
                if (maceTimer <= 0) maceState = MaceState.START_GLIDE;
            }
            case START_GLIDE -> {
                if (!p.isGliding() && !p.isOnGround()) {
                    p.startGliding();
                    p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                maceState = MaceState.GLIDE; maceStateTicks = 0;
            }
            case GLIDE -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                maceStateTicks++;
                selectSlot(p, 0);
                smoothLook(p, maceTarget.getEyePos(), (float)(AIM_SMOOTH.value / 100.0));
                double dist = p.distanceTo(maceTarget);
                boolean above = p.getY() > maceTarget.getY() + 3.0;
                if ((dist < 8.0 && above) || (above && p.getVelocity().y < -0.3 && dist < 12.0)) { maceState = MaceState.STOP_GLIDE; maceTimer = 0; }
                else if (p.isOnGround() || maceStateTicks > 200) resetMace(mc, p);
            }
            case STOP_GLIDE -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; maceTimer = 0; return; }
                if (p.isOnGround()) { resetMace(mc, p); return; }
                maceTimer++;
                if (p.isGliding()) { if (maceTimer > 20) resetMace(mc, p); return; }
                if (fallStartY == 0) fallStartY = p.getY();
                if (maceTimer >= 2) {
                    if (MACE_CRIT_STYLES.on) {
                        maceState = MaceState.CRIT_ANIM;
                        maceTimer = 0; maceStateTicks = 0;
                        critAnimStartYaw = p.getYaw();
                        critAnimStartPitch = p.getPitch();
                        currentAnimStyle = MACE_CRIT_RANDOM.on ? new Random().nextInt(100000) : (int) MACE_CRIT_STYLE.value;
                    } else { maceState = MaceState.PRE_SMASH; maceTimer = 2; }
                }
            }
            case CRIT_ANIM -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                int maxTicks = getAnimTicks(currentAnimStyle);
                float[] anim = getCritAnimation(currentAnimStyle, maceStateTicks, maxTicks);
                float newYaw = critAnimStartYaw + anim[0];
                float newPitch = critAnimStartPitch + anim[1];
                while (newPitch > 90) newPitch -= 180;
                while (newPitch < -90) newPitch += 180;
                p.setYaw(newYaw); p.setPitch(newPitch);
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, p.isOnGround(), p.horizontalCollision));
                maceStateTicks++;
                if (fallStartY == 0) fallStartY = p.getY();
                if (maceStateTicks >= maxTicks) { maceState = MaceState.PRE_SMASH; maceTimer = 2; maceStateTicks = 0; }
                if (p.isOnGround()) { maceState = MaceState.PRE_SMASH; maceTimer = 2; maceStateTicks = 0; }
            }
            case PRE_SMASH -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                selectSlot(p, 0);
                lookAt(p, maceTarget.getEyePos());
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
                maceTimer--;
                if (maceTimer <= 0) { maceState = MaceState.FALL; maceStateTicks = 0; attackAttempts = 0; }
            }
            case FALL -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                selectSlot(p, 0);
                lookAt(p, maceTarget.getEyePos());
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
                maceStateTicks++;
                if (fallStartY == 0) fallStartY = p.getY();
                double fallDist = Math.max(0, fallStartY - p.getY());
                double dist = p.distanceTo(maceTarget);
                boolean canAttack = dist <= 3.0 && fallDist >= 1.5 && p.getAttackCooldownProgress(0f) >= 0.85f && !p.isGliding() && !p.isOnGround();
                if (canAttack) {
                    mc.interactionManager.attackEntity(p, maceTarget);
                    p.swingHand(Hand.MAIN_HAND);
                    spawnAccessoryEffect(mc, p, maceTarget.getPos(), "hit");
                    attackAttempts++;
                    if (attackAttempts >= 2) { maceState = MaceState.COMBO; maceStateTicks = 0; }
                }
                if (maceStateTicks > 30 || p.isOnGround()) { maceState = MaceState.COMBO; maceStateTicks = 0; }
            }
            case COMBO -> {
                if (maceTarget == null || !maceTarget.isAlive()) { resetMace(mc, p); return; }
                selectSlot(p, 0);
                lookAt(p, maceTarget.getEyePos());
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
                maceStateTicks++;
                if (maceStateTicks < 40) {
                    if (p.distanceTo(maceTarget) <= 3.5 && p.getAttackCooldownProgress(0f) >= 0.85f) {
                        mc.interactionManager.attackEntity(p, maceTarget);
                        p.swingHand(Hand.MAIN_HAND);
                    }
                } else resetMace(mc, p);
            }
        }
    }

    private static void resetMace(MinecraftClient mc, ClientPlayerEntity p) {
        if (elytraEquipped) { swapChest(mc, p); elytraEquipped = false; }
        maceState = MaceState.IDLE; maceTarget = null;
        maceTimer = 0; maceStateTicks = 0;
        maceStartY = 0; fallStartY = 0;
        attackAttempts = 0;
        critAnimStartYaw = 0; critAnimStartPitch = 0;
        currentAnimStyle = 0;
    }

    private static void smoothLook(ClientPlayerEntity p, Vec3d target, float smooth) {
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float yawDiff = wrapDegrees(wantYaw - p.getYaw());
        float pitchDiff = wantPitch - p.getPitch();
        p.setYaw(p.getYaw() + yawDiff * smooth);
        p.setPitch(p.getPitch() + pitchDiff * smooth);
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
    }

    private static boolean isKitReady(ClientPlayerEntity p) {
        return p.getInventory().getStack(0).isOf(Items.MACE) && p.getInventory().getStack(1).isOf(Items.WIND_CHARGE)
            && p.getInventory().getStack(2).isOf(Items.ENDER_PEARL) && p.getInventory().getStack(3).isOf(Items.ELYTRA);
    }
    private static int findItemSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int invToHandlerSlot(int s) { return s < 9 ? 36 + s : s; }
    private static void swapChest(MinecraftClient mc, ClientPlayerEntity p) {
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, 6, 3, SlotActionType.SWAP, p);
    }

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
        float yaw = (float)(Math.toDegrees(Math.atan2(ddz, ddx)) - 90.0);
        float pitch = (float)-Math.toDegrees(Math.atan2(ddy, flat));
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround(), p.horizontalCollision));
        float oldY = p.getYaw(), oldP = p.getPitch();
        p.setYaw(yaw); p.setPitch(pitch);
        mc.interactionManager.updateBlockBreakingProgress(spawner, Direction.UP);
        p.swingHand(Hand.MAIN_HAND);
        p.setYaw(oldY); p.setPitch(oldP);
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
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity)
                    chestCache.add(pos.toImmutable());
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
            mineCooldown = 0; return;
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
            selectSlot(p, blockSlot);
            BlockPos placeAgainst = null;
            Direction placeFace = null;
            Direction behind = p.getHorizontalFacing().getOpposite();
            BlockPos backNeighbor = under.offset(behind);
            if (mc.world.getBlockState(backNeighbor).isSolidBlock(mc.world, backNeighbor)) { placeAgainst = backNeighbor; placeFace = behind.getOpposite(); }
            else {
                for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos neighbor = under.offset(dir);
                    if (mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) { placeAgainst = neighbor; placeFace = dir.getOpposite(); break; }
                }
            }
            if (placeAgainst != null) {
                Vec3d hitVec = Vec3d.ofCenter(placeAgainst).add(placeFace.getOffsetX() * 0.5, placeFace.getOffsetY() * 0.5, placeFace.getOffsetZ() * 0.5);
                Vec3d eye = p.getEyePos();
                double dx = hitVec.x - eye.x, dy = hitVec.y - eye.y, dz = hitVec.z - eye.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround(), p.horizontalCollision));
                float oldY = p.getYaw(), oldP = p.getPitch();
                p.setYaw(yaw); p.setPitch(pitch);
                p.swingHand(Hand.MAIN_HAND);
                BlockHitResult hit = new BlockHitResult(hitVec, placeFace, placeAgainst, false);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                if (!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) { mc.options.backKey.setPressed(true); scaffoldTimer = 3; }
                p.setYaw(oldY); p.setPitch(oldP);
                selectSlot(p, oldSlot);
            }
        }
    }

    static void findStorage(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        scanChests(mc, p);
        storageResults.clear(); storageResults.addAll(chestCache);
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
            chestCooldown = 2; return;
        }
    }

    static void autoLogin(MinecraftClient mc) { if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("login yourpassword"); }
    static void autoRegister(MinecraftClient mc) { if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("register yourpassword yourpassword"); }

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
                selectSlot(p, axeSlot);
                Vec3d eye = p.getEyePos(); Vec3d pos = target.getEyePos();
                double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
                double flat = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround(), p.horizontalCollision));
                mc.interactionManager.attackEntity(p, target); p.swingHand(Hand.MAIN_HAND);
                auraCooldown = 2 + new Random().nextInt(2); return;
            }
        }
        if (CRITICALS.on && p.isOnGround() && p.getVelocity().y == 0 && !p.isSprinting() && !p.isTouchingWater()) { p.jump(); auraCooldown = 2; return; }
        if (W_TAP.on && p.isSprinting()) { mc.options.sprintKey.setPressed(false); wTapTimer = 1; }
        Vec3d eye = p.getEyePos(); Vec3d pos = target.getEyePos();
        double dx = pos.x - eye.x, dy = pos.y - eye.y, dz = pos.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float jy = (float) ((Math.random() - 0.5) * 0.08), jp = (float) ((Math.random() - 0.5) * 0.08);
        float rotY = (float) (Math.round((p.getYaw() + wrapDegrees(yaw - p.getYaw())) / sens) * sens) + jy;
        float rotP = (float) (Math.round((p.getPitch() + (pitch - p.getPitch())) / sens) * sens) + jp;
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rotY, rotP, p.isOnGround(), p.horizontalCollision));
        mc.interactionManager.attackEntity(p, target); p.swingHand(Hand.MAIN_HAND);
        spawnAccessoryEffect(mc, p, target.getPos(), "hit");
        auraCooldown = Math.max(1, (getPing(mc) / 50) + 1 + new Random().nextInt(2));
    }

    private static void aimAssist(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || KILLAURA.on || MACE_KILLER.on) return;
        Vec3d eye = p.getEyePos();
        PlayerEntity target = null; double bestAngle = 35.0;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isBot(o, mc)) continue;
            double d = p.distanceTo(o);
            if (d > 8.0) continue;
            Vec3d tEye = o.getEyePos();
            double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float wy = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float wp = (float) -Math.toDegrees(Math.atan2(dy, flat));
            float angle = Math.max(Math.abs(wrapDegrees(wy - p.getYaw())), Math.abs(wp - p.getPitch()));
            if (angle < bestAngle) { bestAngle = angle; target = o; }
        }
        if (target == null) return;
        Vec3d tEye = target.getEyePos();
        double dx = tEye.x - eye.x, dy = tEye.y - eye.y, dz = tEye.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float wy = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wp = (float) -Math.toDegrees(Math.atan2(dy, flat));
        float speedF = (float) Math.min(1.0, AIM_SPEED.value / 100.0);
        float sens = AIM_MODE.value == 0 ? 0.15f : AIM_MODE.value == 1 ? 0.10f : 0.05f;
        float fy = p.getYaw() + wrapDegrees(wy - p.getYaw()) * speedF;
        float fp = p.getPitch() + (wp - p.getPitch()) * speedF;
        fy += (float) ((Math.random() - 0.5) * 0.06);
        fp += (float) ((Math.random() - 0.5) * 0.06);
        fy = (float) (Math.round(fy / sens) * sens);
        fp = (float) (Math.round(fp / sens) * sens);
        p.setYaw(fy); p.setPitch(fp);
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    private static int swordRank(Item item) {
        if (item == Items.NETHERITE_SWORD) return 8;
        if (item == Items.DIAMOND_SWORD) return 7;
        if (item == Items.IRON_SWORD) return 6;
        if (item == Items.STONE_SWORD) return 5;
        if (item == Items.NETHERITE_AXE) return 4;
        if (item == Items.DIAMOND_AXE) return 3;
        if (item == Items.GOLDEN_SWORD) return 2;
        if (item == Items.WOODEN_SWORD) return 1;
        return 0;
    }

    private static void autoSword(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || mc.options.attackKey.isPressed() || MACE_KILLER.on) return;
        PlayerEntity target = null; double bestD = 36;
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
        if (bestSlot != -1 && p.getInventory().getSelectedSlot() != bestSlot) selectSlot(p, bestSlot);
    }

    private static void bowAimbot(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || !p.isUsingItem()) return;
        ItemStack active = p.getActiveItem();
        if (!(active.isOf(Items.BOW) || active.isOf(Items.CROSSBOW))) return;
        PlayerEntity target = null; double bestD = 2500;
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
        if (mc.currentScreen != null || p.isUsingItem()) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity c && p.distanceTo(c) < 5.0) {
                lookAt(p, new Vec3d(c.getX(), c.getY(), c.getZ()));
                mc.interactionManager.attackEntity(p, c); p.swingHand(Hand.MAIN_HAND); return;
            }
        }
        int crystalSlot = -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) { crystalSlot = i; break; }
        if (crystalSlot == -1) return;
        BlockPos placePos = findCrystalSpot(mc, p);
        if (placePos == null) return;
        selectSlot(p, crystalSlot);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(placePos), Direction.UP, placePos, false);
        lookAt(p, Vec3d.ofCenter(placePos));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit); p.swingHand(Hand.MAIN_HAND);
    }

    private static BlockPos findCrystalSpot(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -2; dy <= 3; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            BlockState st = mc.world.getBlockState(pos);
            if ((st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK)) && mc.world.isAir(pos.up()))
                if (p.squaredDistanceTo(pos.toCenterPos()) <= 25) return pos;
        }
        return null;
    }

    private static void anchorAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || p.isUsingItem()) return;
        BlockPos anchor = findAnchor(mc, p);
        if (anchor == null) return;
        if (mc.world.getBlockState(anchor).isOf(Blocks.RESPAWN_ANCHOR)) {
            int glowSlot = -1;
            for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.GLOWSTONE)) { glowSlot = i; break; }
            if (glowSlot != -1) {
                selectSlot(p, glowSlot);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
                lookAt(p, Vec3d.ofCenter(anchor));
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit); p.swingHand(Hand.MAIN_HAND); return;
            }
        }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(anchor), Direction.UP, anchor, false);
        lookAt(p, Vec3d.ofCenter(anchor));
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit); p.swingHand(Hand.MAIN_HAND);
    }

    private static BlockPos findAnchor(MinecraftClient mc, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int dy = -3; dy <= 3; dy++) for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            BlockPos pos = base.add(dx, dy, dz);
            if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR))
                if (p.squaredDistanceTo(pos.toCenterPos()) <= 36) return pos;
        }
        return null;
    }

    private static void autoTrap(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null || trapCooldown > 0) { if (trapCooldown > 0) trapCooldown--; return; }
        PlayerEntity target = null; double bestD = 25;
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
        selectSlot(p, blockSlot);
        for (BlockPos pos : surrounds) {
            if (mc.world.getBlockState(pos).isAir()) {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                lookAt(p, Vec3d.ofCenter(pos));
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit); p.swingHand(Hand.MAIN_HAND);
                trapCooldown = 2; return;
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
                totemCooldown = cd; return;
            }
        }
    }

    private static void autoArmor(MinecraftClient mc, ClientPlayerEntity p) {
        if (armorCooldown > 0) { armorCooldown--; return; }
        if (mc.currentScreen != null || p.currentScreenHandler != p.playerScreenHandler) return;
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        for (EquipmentSlot slot : slots) {
            if (p.getEquippedStack(slot).isEmpty()) {
                for (int i = 9; i <= 44; i++) {
                    ItemStack st = p.playerScreenHandler.getSlot(i).getStack();
                    EquippableComponent eq = st.get(DataComponentTypes.EQUIPPABLE);
                    if (eq != null && eq.slot() == slot) {
                        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, p);
                        armorCooldown = 10; return;
                    }
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
                var potion = st.get(DataComponentTypes.POTION_CONTENTS);
                if (potion != null && potion.potion().isPresent()) {
                    String name = potion.potion().get().value().toString().toLowerCase();
                    if (!name.contains("healing") && !name.contains("regeneration")) continue;
                }
                int old = p.getInventory().getSelectedSlot();
                selectSlot(p, i);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                selectSlot(p, old);
                potCooldown = 40; return;
            }
        }
    }

    private static void autoGapple(MinecraftClient mc, ClientPlayerEntity p) {
        if (gappleCooldown > 0) { gappleCooldown--; return; }
        if (mc.currentScreen != null || p.isUsingItem() || p.getHealth() > 8) return;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(Items.ENCHANTED_GOLDEN_APPLE) || st.isOf(Items.GOLDEN_APPLE)) {
                int old = p.getInventory().getSelectedSlot();
                selectSlot(p, i);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                selectSlot(p, old);
                gappleCooldown = 35; return;
            }
        }
    }

    private static void step(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) return;
        if (stepCooldown > 0) { stepCooldown--; return; }
        if (!p.isOnGround() || !p.horizontalCollision) return;
        if (!(mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed())) return;
        Vec3d v = p.getVelocity(); p.setVelocity(v.x, 0.42, v.z); stepCooldown = 4;
    }

    private static void noFall(MinecraftClient mc, ClientPlayerEntity p) {
        if (MACE_KILLER.on && (maceState == MaceState.STOP_GLIDE || maceState == MaceState.CRIT_ANIM
                || maceState == MaceState.PRE_SMASH || maceState == MaceState.FALL || maceState == MaceState.COMBO)) return;
        if (p.fallDistance > 2.0f) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
            p.fallDistance = 0f;
        }
    }

    private static void fullbright(ClientPlayerEntity p) {
        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 220)
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
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
        double cosY = Math.cos(Math.toRadians(-yaw)), sinY = Math.sin(Math.toRadians(-yaw));
        Vec3d r1 = new Vec3d(delta.x * cosY + delta.z * sinY, delta.y, -delta.x * sinY + delta.z * cosY);
        double cosP = Math.cos(Math.toRadians(-pitch)), sinP = Math.sin(Math.toRadians(-pitch));
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
        int err = dx + dy, steps = 0;
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
        if (p == null || mc.world == null || mc.options.hudHidden || mc.currentScreen instanceof Menu || mc.currentScreen instanceof LoginScreen) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (!authenticated) {
            ctx.drawTextWithShadow(tr, "§c§lNOT LOGGED IN §7— Press §fNumpad 5", 4, 4, 0xFFFF5555);
            return;
        }

        Vec3d camPos = p.getEyePos();
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        double fov = mc.options.getFov().getValue();
        double focal = (sh / 2.0) / Math.tan(Math.toRadians(fov / 2.0));

        if (TRACERS.on) {
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == p || isBot(o, mc)) continue;
                if (p.distanceTo(o) > 96) continue;
                int[] sc = projectToScreen(new Vec3d(o.getX(), o.getY() + o.getHeight() / 2.0, o.getZ()), camPos, camYaw, camPitch, sw, sh, focal);
                if (sc == null) continue;
                ctx.fill(sw / 2, sh - 1, sc[0], sh + 1, 0xFF00E5FF);
            }
        }

        if (BASE_FINDER.on && !chestCache.isEmpty()) {
            int cx = sw / 2, cy = sh / 2;
            Vec3d myPos = new Vec3d(p.getX(), p.getY(), p.getZ());
            for (BlockPos cp : chestCache) {
                double d = myPos.distanceTo(Vec3d.ofCenter(cp));
                if (d > 512) continue;
                int[] sc = projectToScreen(Vec3d.ofCenter(cp), camPos, camYaw, camPitch, sw, sh, focal);
                if (sc == null) continue;
                int color = d < 64 ? 0xFFFFAA00 : (d < 128 ? 0xFFFFCC00 : 0xFFFFEE00);
                drawLine(ctx, cx, cy, sc[0], sc[1], color);
                ctx.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, color);
                ctx.drawTextWithShadow(tr, (int)d + "m", sc[0] + 3, sc[1] - 3, 0xFFFFAA00);
            }
        }

        int y = 4;
        if (adminAuthenticated) { ctx.drawTextWithShadow(tr, "§6§l👑 ADMIN", 4, y, 0xFFFFAA00); y += 12; }
        else if (authenticatedVip) { ctx.drawTextWithShadow(tr, "§6§l★ VIP", 4, y, 0xFFFFAA00); y += 12; }
        if (WATERMARK.on) {
            long t = System.currentTimeMillis() / 100;
            int r = (int)(Math.sin(t * 0.05) * 80 + 175), g = (int)(Math.sin(t * 0.05 + 2) * 80 + 175), b = (int)(Math.sin(t * 0.05 + 4) * 80 + 175);
            ctx.drawTextWithShadow(tr, NAME + " §7" + VERSION, 4, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            y += 14;
        }
        if (PING_HUD.on) {
            PlayerListEntry e = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerListEntry(p.getUuid()) : null;
            int ping = e != null ? e.getLatency() : 0;
            ctx.drawTextWithShadow(tr, "§7Ping: §f" + ping + "ms", 4, y, 0xFFFFFFFF); y += 11;
        }
        if (FPS_HUD.on) { ctx.drawTextWithShadow(tr, "§7FPS: §f" + mc.getCurrentFps(), 4, y, 0xFFFFFFFF); y += 11; }
        if (ARRAYLIST.on) {
            List<Mod> active = new ArrayList<>();
            for (Mod m : MODS) if (m.on && !m.action && m.cat != Cat.HUD) active.add(m);
            active.sort(Comparator.comparingInt((Mod m) -> -tr.getWidth(m.name.replaceAll("§.",""))));
            int ay = 4;
            for (Mod m : active) {
                long t = System.currentTimeMillis() / 100;
                int ar = (int)(Math.sin(t * 0.05 + ay * 0.1) * 80 + 175);
                int ag = (int)(Math.sin(t * 0.05 + 2 + ay * 0.1) * 80 + 175);
                int ab = (int)(Math.sin(t * 0.05 + 4 + ay * 0.1) * 80 + 175);
                ctx.drawTextWithShadow(tr, m.name, sw - tr.getWidth(m.name.replaceAll("§.","")) - 4, ay, 0xFF000000 | (ar << 16) | (ag << 8) | ab);
                ay += 11;
            }
        }
    }

    // ============ LOGIN ============
    static class LoginScreen extends Screen {
        private TextFieldWidget codeField;
        private String errorMsg = "", enteredCode = "";
        LoginScreen() { super(Text.literal("Kinger Login")); }

        @Override protected void init() {
            int cx = this.width / 2, cy = this.height / 2;
            codeField = new TextFieldWidget(this.textRenderer, cx - 100, cy - 10, 200, 20, Text.literal("Code"));
            codeField.setMaxLength(64);
            codeField.setChangedListener(s -> enteredCode = s);
            addDrawableChild(codeField);
            setInitialFocus(codeField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Login"), b -> attemptLogin()).dimensions(cx - 100, cy + 20, 95, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(cx + 5, cy + 20, 95, 20).build());
        }

        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { attemptLogin(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void attemptLogin() {
            String trimmed = enteredCode.trim();
            boolean isNormal = false, isVip = false, isAdmin = false;
            for (String c : VALID_CODES) if (c.equals(trimmed)) { isNormal = true; break; }
            for (String c : VIP_CODES) if (c.equals(trimmed)) { isVip = true; break; }
            for (String c : ADMIN_CODES) if (c.equals(trimmed)) { isAdmin = true; break; }
            for (String c : customUserCodes) if (c.equals(trimmed)) { isNormal = true; break; }
            for (String c : customVipCodes) if (c.equals(trimmed)) { isVip = true; break; }
            for (String c : customAdminCodes) if (c.equals(trimmed)) { isAdmin = true; break; }
            if (isNormal || isVip || isAdmin) {
                authenticated = true;
                authenticatedVip = isVip || isAdmin;
                adminAuthenticated = isAdmin;
                save();
                if (this.client != null && this.client.player != null) {
                    if (isAdmin) this.client.player.sendMessage(Text.literal("§6§l[👑 ADMIN] §aWelcome, Boss. Full control enabled."), false);
                    else if (isVip) this.client.player.sendMessage(Text.literal("§6§l[★ VIP] §aLogin successful!"), false);
                    else this.client.player.sendMessage(Text.literal("§a§l[✓] §aLogin successful!"), false);
                }
                close();
                if (this.client != null) this.client.setScreen(new Menu());
            } else {
                errorMsg = "§cInvalid code!";
                enteredCode = "";
                if (codeField != null) codeField.setText("");
            }
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, this.width, this.height, 0xF0080810);
            int cx = this.width / 2, cy = this.height / 2;
            for (int i = 0; i < 4; i++) {
                int alpha = 0x60 >> i;
                ctx.fill(cx - 130 - i, cy - 90 - i, cx + 130 + i, cy + 90 + i, (alpha << 24) | 0x00E5FF);
            }
            ctx.fill(cx - 130, cy - 90, cx + 130, cy + 90, 0xF00E0E16);
            ctx.fill(cx - 130, cy - 90, cx + 130, cy - 88, 0xFF00E5FF);
            ctx.fill(cx - 130, cy + 88, cx + 130, cy + 90, 0xFF00E5FF);
            long t = System.currentTimeMillis() / 100;
            int r = (int)(Math.sin(t * 0.05) * 80 + 175), g = (int)(Math.sin(t * 0.05 + 2) * 80 + 175), b = (int)(Math.sin(t * 0.05 + 4) * 80 + 175);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§lK I N G E R", cx, cy - 70, 0xFF000000 | (r << 16) | (g << 8) | b);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§7License Login System", cx, cy - 55, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§7Enter your access code below:", cx, cy - 30, 0xFF888888);
            super.render(ctx, mouseX, mouseY, delta);
            if (!errorMsg.isEmpty()) ctx.drawCenteredTextWithShadow(this.textRenderer, errorMsg, cx, cy + 50, 0xFFFF5555);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "§8ENTER to submit §7| §8ESC to close", cx, cy + 70, 0xFF666666);
        }

        @Override public boolean shouldPause() { return false; }
    }

    // ============ MENU ============
    enum Kind { TAB, TOGGLE, ACTION, STEP, VALUE }
    record Widget(int x, int y, int w, int h, String label, Kind kind, BooleanSupplier active, String desc, Supplier<String> tag, boolean vipLocked) {}

    static class Menu extends Screen {
        static final int PW = 540, PH = 440;
        private final List<Widget> widgets = new ArrayList<>();
        final int BG = 0xF00E0E16, PANEL = 0xF01C1C26, ACCENT = 0xFF00E5FF, ACCENT2 = 0xFFFF00AA;
        final int TXT = 0xFFEEEEEE, TXT_D = 0xFF666677;
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
            int sx = px + 10, sy = py + 50, i = 0;
            for (Cat c : Cat.values()) {
                if (c == Cat.ADMIN && !adminAuthenticated) continue;
                if (c == Cat.ACCESSORY && !adminAuthenticated) continue;
                final Cat cc = c; int ty = sy + i * 24;
                hit(sx, ty, 120, 22, () -> { lastTab = cc; save(); clearAndInit(); });
                widgets.add(new Widget(sx, ty, 120, 22, c.label, Kind.TAB, () -> lastTab == cc, null, null, false));
                i++;
            }
            int cx = px + 140, cy = py + 50, col = 0, row = 0;
            for (Mod m : MODS) {
                if (m.cat != lastTab) continue;
                int mx = cx + col * 195, my = cy + row * 28;
                boolean locked = m.vip && !authenticatedVip && !adminAuthenticated;
                hit(mx, my, 190, 24, () -> {
                    if (locked) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) mc.player.sendMessage(Text.literal("§6§l[★ VIP] §cRequires VIP access!"), true);
                        return;
                    }
                    if (m.cat == Cat.ADMIN && !adminAuthenticated) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) mc.player.sendMessage(Text.literal("§6§l[👑 ADMIN] §cRequires admin access!"), true);
                        return;
                    }
                    if (m.action) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (m == STORAGE) { close(); findStorage(mc); }
                        else if (m == AUTOLOGIN) { close(); autoLogin(mc); }
                        else if (m == AUTOREGISTER) { close(); autoRegister(mc); }
                        else if (m == ADMIN_ALLON) { adminToggleAll(true); clearAndInit(); }
                        else if (m == ADMIN_ALLOFF) { adminToggleAll(false); clearAndInit(); }
                        else if (m == ADMIN_RELOAD) { load(); clearAndInit(); }
                        else if (m == ADMIN_SAVE) { save(); if (mc.player != null) mc.player.sendMessage(Text.literal("§aConfig saved!"), true); }
                        else if (m == ADMIN_GENS) {
                            String code = adminGenerateCode();
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal("§6§l[VIP CODE] §f" + code), false);
                                mc.player.sendMessage(Text.literal("§7Share this with who you want to give VIP."), false);
                            }
                        }
                        else if (m == ADMIN_CLEARCODES) {
                            customVipCodes.clear(); customUserCodes.clear(); customAdminCodes.clear(); save();
                            if (mc.player != null) mc.player.sendMessage(Text.literal("§cAll custom codes removed!"), true);
                        }
                    } else { m.on = !m.on; save(); }
                });
                widgets.add(new Widget(mx, my, 190, 24, m.name, m.action ? Kind.ACTION : Kind.TOGGLE, () -> m.on, m.desc, null, locked));
                col++; if (col == 2) { col = 0; row++; }
            }
            if (col == 1) row++;
            for (Num n : NUMS) {
                if (n.cat != lastTab) continue;
                int ny = cy + row * 30;
                widgets.add(new Widget(cx, ny, 280, 22, n.name, Kind.VALUE, () -> false, n.desc, () -> String.valueOf(n.value), false));
                hit(cx + 285, ny, 22, 22, () -> { n.change(-1); save(); });
                widgets.add(new Widget(cx + 285, ny, 22, 22, "-", Kind.STEP, () -> false, null, null, false));
                hit(cx + 312, ny, 22, 22, () -> { n.change(1); save(); });
                widgets.add(new Widget(cx + 312, ny, 22, 22, "+", Kind.STEP, () -> false, null, null, false));
                row++;
            }

            if (lastTab == Cat.ACCESSORY) {
                int controlsY = cy + row * 30;
                hit(cx, controlsY, 130, 22, () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null) {
                        Accessory acc = generateAccessory((long) ACCESSORY_ID.value);
                        spawnAccessoryEffect(mc, mc.player, mc.player.getPos(), "preview");
                        mc.player.sendMessage(Text.literal("§d§l[Test] §f" + acc.name), true);
                    }
                });
                widgets.add(new Widget(cx, controlsY, 130, 22, "§d§l▶ Test Now", Kind.ACTION, () -> false, "Spawn at feet", () -> "", false));

                hit(cx + 135, controlsY, 130, 22, () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null) {
                        long max = getMaxAccessoryId();
                        long randId = (long)(new Random().nextDouble() * max);
                        ACCESSORY_ID.value = randId;
                        Accessory acc = generateAccessory(randId);
                        spawnAccessoryEffect(mc, mc.player, mc.player.getPos(), "preview");
                        mc.player.sendMessage(Text.literal("§d§l[Random] §f" + acc.name), true);
                    }
                });
                widgets.add(new Widget(cx + 135, controlsY, 130, 22, "§6§l🎲 Random", Kind.ACTION, () -> false, "Random ID", () -> "", false));

                row++;
                long maxId = getMaxAccessoryId();
                String tierText = "§7Limit: " + formatTierLabel() + " §7| Current: §f" + (long)ACCESSORY_ID.value + " §7/ §f" + maxId;
                widgets.add(new Widget(cx, controlsY + 26, 265, 14, tierText, Kind.VALUE, () -> false, "Your tier limit", () -> "", false));
            }

            int cmdY = py + PH - 55;
            TextFieldWidget cf = new TextFieldWidget(this.textRenderer, px + 145, cmdY, 385, 20, Text.literal("Auto Command"));
            cf.setText(autoCommandText); cf.setMaxLength(256);
            cf.setChangedListener(text -> autoCommandText = text);
            addDrawableChild(cf);
        }

        @Override public void removed() { save(); }

        @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
            float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 250f);
            ctx.fill(0, 0, this.width, this.height, ((int)(0xAA * t)) << 24);
            int px = px(), py = py();
            for (int i = 0; i < 5; i++) {
                int a = (int)(0x40 * t) >> i;
                ctx.fill(px - 3 - i, py - 3 - i, px + PW + 3 + i, py + PH + 3 + i, (a << 24) | 0x00E5FF);
            }
            ctx.fill(px, py, px + PW, py + PH, BG);
            ctx.fill(px, py, px + 140, py + PH, PANEL);
            ctx.fill(px, py, px + PW, py + 36, 0xF0080810);
            ctx.fill(px, py + 36, px + PW, py + 37, ACCENT);
            int scanY = py + 38 + (int)((System.currentTimeMillis() / 4) % (PH - 38));
            if (scanY < py + PH) ctx.fill(px, scanY, px + PW, scanY + 1, 0x2000E5FF);
            ctx.fill(px + 138, py, px + 140, py + PH, ACCENT);
        }

        @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            TextRenderer tr = this.textRenderer;
            int px = px(), py = py();
            long t = System.currentTimeMillis() / 100;
            int r = (int)(Math.sin(t * 0.05) * 80 + 175), g = (int)(Math.sin(t * 0.05 + 2) * 80 + 175), b = (int)(Math.sin(t * 0.05 + 4) * 80 + 175);
            ctx.drawTextWithShadow(tr, NAME, px + 14, py + 13, 0xFF000000 | (r << 16) | (g << 8) | b);
            String ver = adminAuthenticated ? "§6👑 ADMIN " + VERSION : (authenticatedVip ? "§6★ VIP " + VERSION : VERSION);
            int verColor = adminAuthenticated ? 0xFFFFAA00 : 0xFFFF00AA;
            String cleanVer = ver.replaceAll("§.","");
            ctx.drawTextWithShadow(tr, ver, px + PW - tr.getWidth(cleanVer) - 14, py + 13, verColor);
            int ci = 0;
            for (Cat c : Cat.values()) {
                if (c == Cat.ADMIN && !adminAuthenticated) continue;
                if (c == Cat.ACCESSORY && !adminAuthenticated) continue;
                if (c == lastTab) { ctx.fill(px + 132, py + 50 + ci * 24, px + 140, py + 50 + ci * 24 + 22, ACCENT2); break; }
                ci++;
            }
            for (Widget w : widgets) {
                boolean hov = mouseX >= w.x() && mouseX < w.x() + w.w() && mouseY >= w.y() && mouseY < w.y() + w.h();
                boolean on = w.active().getAsBoolean();
                if (w.kind() == Kind.TAB) {
                    if (on) { ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), 0xFF2A2A38); ctx.fill(w.x(), w.y(), w.x() + 4, w.y() + w.h(), ACCENT); }
                    else if (hov) ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), 0xFF252532);
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 12, w.y() + (w.h() - 8) / 2, on ? ACCENT : TXT);
                } else if (w.kind() == Kind.TOGGLE) {
                    if (w.vipLocked) {
                        ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), 0xFF1A1A22);
                        ctx.drawTextWithShadow(tr, w.label(), w.x() + 8, w.y() + (w.h() - 8) / 2, 0xFF884400);
                        ctx.drawTextWithShadow(tr, "§6★VIP", w.x() + w.w() - 38, w.y() + (w.h() - 8) / 2, 0xFFFFAA00);
                    } else {
                        boolean isGod = w.label().contains("§6§l👑") || w.label().contains("§c§l🔥") || w.label().contains("§6§l✨");
                        int bgC = on ? (isGod ? 0xFFFFAA00 : 0xFF0088AA) : 0xFF333340;
                        if (hov) bgC = on ? ACCENT : 0xFF444455;
                        ctx.drawTextWithShadow(tr, w.label(), w.x() + 8, w.y() + (w.h() - 8) / 2, on ? 0xFFFFFFFF : TXT_D);
                        int tx = w.x() + w.w() - 34, ty = w.y() + 6;
                        ctx.fill(tx, ty, tx + 26, ty + 12, bgC);
                        int cx2 = on ? tx + 15 : tx + 2;
                        ctx.fill(cx2, ty + 2, cx2 + 8, ty + 10, 0xFFFFFFFF);
                    }
                } else if (w.kind() == Kind.VALUE) {
                    ctx.drawTextWithShadow(tr, w.label(), w.x(), w.y() + 7, TXT);
                    ctx.drawTextWithShadow(tr, w.tag().get(), w.x() + w.w() - tr.getWidth(w.tag().get()) - 3, w.y() + 7, ACCENT);
                } else if (w.kind() == Kind.STEP) {
                    int bg = hov ? 0xFF444455 : 0xFF222230;
                    ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                    int tw = tr.getWidth(w.label());
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + (w.w() - tw) / 2, w.y() + (w.h() - 8) / 2, TXT);
                } else if (w.kind() == Kind.ACTION) {
                    int bg = hov ? 0xFF2A2A38 : PANEL;
                    ctx.fill(w.x(), w.y(), w.x() + w.w(), w.y() + w.h(), bg);
                    ctx.drawTextWithShadow(tr, w.label(), w.x() + 8, w.y() + (w.h() - 8) / 2, ACCENT);
                }
            }
            ctx.drawTextWithShadow(tr, "§b§lAuto Command §7(press §fK§7 to open)", px + 145, py + PH - 72, 0xFFFFFFFF);
        }
        @Override public boolean shouldPause() { return false; }
    }
    }
