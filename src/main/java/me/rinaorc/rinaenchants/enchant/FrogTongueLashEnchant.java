package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.FrogTongueAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Frog Tongue Lash / Coup de Langue
 *
 * 3 grenouilles de couleurs différentes apparaissent et attrapent
 * les cultures avec leur langue élastique!
 *
 * Types de grenouilles:
 * - 🟠 Orange (WARM) = Langue courte mais rapide
 * - ⚪ Blanc (COLD) = Langue longue mais lente
 * - 🟢 Vert (TEMPERATE) = Langue moyenne + rebond (attrape 2 cultures)
 *
 * Système de combo:
 * - Si 3 grenouilles lèchent la même zone → COMBO TONGUE!
 * - Bonus visuel et sonore spectaculaire
 *
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class FrogTongueLashEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public FrogTongueLashEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ FrogTongueLash: " + CROPS.size() + " types de cultures chargés!");
    }

    private void initCrops() {
        // Cultures classiques
        CROPS.add(Material.WHEAT);
        CROPS.add(Material.CARROTS);
        CROPS.add(Material.POTATOES);
        CROPS.add(Material.BEETROOTS);
        CROPS.add(Material.NETHER_WART);
        CROPS.add(Material.COCOA);
        CROPS.add(Material.SWEET_BERRY_BUSH);

        // Coraux
        CROPS.add(Material.TUBE_CORAL);
        CROPS.add(Material.BUBBLE_CORAL);
        CROPS.add(Material.BRAIN_CORAL);
        CROPS.add(Material.FIRE_CORAL);
        CROPS.add(Material.HORN_CORAL);
        CROPS.add(Material.TUBE_CORAL_BLOCK);
        CROPS.add(Material.BUBBLE_CORAL_BLOCK);
        CROPS.add(Material.BRAIN_CORAL_BLOCK);
        CROPS.add(Material.FIRE_CORAL_BLOCK);
        CROPS.add(Material.HORN_CORAL_BLOCK);

        // Racines nether
        CROPS.add(Material.WARPED_ROOTS);
        CROPS.add(Material.CRIMSON_ROOTS);
        CROPS.add(Material.NETHER_SPROUTS);
        CROPS.add(Material.TWISTING_VINES);
        CROPS.add(Material.WEEPING_VINES);

        // Fleurs
        CROPS.add(Material.LILAC);
        CROPS.add(Material.ROSE_BUSH);
        CROPS.add(Material.PEONY);
        CROPS.add(Material.SUNFLOWER);
        CROPS.add(Material.TALL_GRASS);
        CROPS.add(Material.LARGE_FERN);

        // Pousses
        CROPS.add(Material.OAK_SAPLING);
        CROPS.add(Material.BIRCH_SAPLING);
        CROPS.add(Material.JUNGLE_SAPLING);
        CROPS.add(Material.SPRUCE_SAPLING);
        CROPS.add(Material.CHERRY_SAPLING);
        CROPS.add(Material.ACACIA_SAPLING);
        CROPS.add(Material.DARK_OAK_SAPLING);
        CROPS.add(Material.MANGROVE_PROPAGULE);

        // Autres
        CROPS.add(Material.MELON);
        CROPS.add(Material.PUMPKIN);
        CROPS.add(Material.SUGAR_CANE);
        CROPS.add(Material.CACTUS);
        CROPS.add(Material.BAMBOO);
        CROPS.add(Material.KELP);
        CROPS.add(Material.KELP_PLANT);
        CROPS.add(Material.SEA_PICKLE);
        CROPS.add(Material.CHORUS_FLOWER);
        CROPS.add(Material.CHORUS_PLANT);

        // Cultures sans âge
        NO_AGE_CROPS.addAll(Arrays.asList(
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
            Material.BAMBOO, Material.KELP, Material.KELP_PLANT,
            Material.TUBE_CORAL, Material.BUBBLE_CORAL, Material.BRAIN_CORAL,
            Material.FIRE_CORAL, Material.HORN_CORAL,
            Material.TUBE_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
            Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            Material.WARPED_ROOTS, Material.CRIMSON_ROOTS, Material.NETHER_SPROUTS,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.SUNFLOWER,
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING,
            Material.SPRUCE_SAPLING, Material.CHERRY_SAPLING, Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
            Material.CHORUS_FLOWER, Material.CHORUS_PLANT, Material.SEA_PICKLE
        ));
    }

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("frog-tongue-lash.enchant-id", "frog_tongue_lash");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel,
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE: Empêche les proc récursifs
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§e[DEBUG] FrogTongueLash: Ignoré - cassé par une entité");
            }
            return;
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] FrogTongueLash proc! Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // RÉCUPÉRER LES PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("frog-tongue-lash.max-level", 100);
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Portée de base de la langue (sera modifiée par type de grenouille)
        int baseTongueRange = plugin.getConfig().getInt("frog-tongue-lash.base-tongue-range", 4);
        int maxTongueRange = plugin.getConfig().getInt("frog-tongue-lash.max-tongue-range", 8);
        int tongueRange = (int) (baseTongueRange + levelRatio * (maxTongueRange - baseTongueRange));

        // Cadence de tir de base (ticks entre chaque lick)
        int baseFireRate = plugin.getConfig().getInt("frog-tongue-lash.base-fire-rate", 15);
        int minFireRate = plugin.getConfig().getInt("frog-tongue-lash.min-fire-rate", 5);
        int fireRate = (int) (baseFireRate - levelRatio * (baseFireRate - minFireRate));

        // Durée de l'effet
        int baseDuration = plugin.getConfig().getInt("frog-tongue-lash.base-duration", 100);
        int maxDuration = plugin.getConfig().getInt("frog-tongue-lash.max-duration", 300);
        int duration = (int) (baseDuration + levelRatio * (maxDuration - baseDuration));

        // Nombre de grenouilles
        int baseFrogCount = plugin.getConfig().getInt("frog-tongue-lash.base-frog-count", 3);
        int maxFrogCount = plugin.getConfig().getInt("frog-tongue-lash.max-frog-count", 6);
        int frogCount = (int) (baseFrogCount + levelRatio * (maxFrogCount - baseFrogCount));

        boolean showParticles = plugin.getConfig().getBoolean("frog-tongue-lash.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("frog-tongue-lash.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("frog-tongue-lash.client-side-only", true);

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] FrogTongueLash - Portée: " + tongueRange +
                ", Cadence: " + fireRate + "t, Durée: " + duration + "t, Grenouilles: " + frogCount);
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // MESSAGE DE DÉBUT
        // ═══════════════════════════════════════════════════════════

        if (plugin.getConfig().getBoolean("frog-tongue-lash.start-message", true)) {
            String message = plugin.getConfig().getString("frog-tongue-lash.start-message-text",
                "&a&l🐸 &2Des grenouilles affamées apparaissent!");
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }

        if (playSound) {
            player.playSound(cropLocation, Sound.ENTITY_FROG_LONG_JUMP, 1.0f, 0.8f);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉER L'ANIMATION DES GRENOUILLES
        // ═══════════════════════════════════════════════════════════

        FrogTongueAnimation animation = new FrogTongueAnimation(
            plugin, cropLocation, player,
            tongueRange, fireRate, duration, frogCount,
            showParticles, clientSideOnly
        );

        // Callback quand une culture est attrapée
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, cropLoc, "frog-tongue-lash");
            }
        });

        // Callback combo tongue
        animation.setOnComboTongue((comboCount) -> {
            if (plugin.getConfig().getBoolean("frog-tongue-lash.combo-message", true)) {
                String message = plugin.getConfig().getString("frog-tongue-lash.combo-message-text",
                    "&a&l🐸 &2&lCOMBO TONGUE! &ax{combo}");
                message = message.replace("{combo}", String.valueOf(comboCount));
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }
        });

        // Callback fin
        animation.setOnFinish((totalCrops, totalCombos) -> {
            if (plugin.getConfig().getBoolean("frog-tongue-lash.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("frog-tongue-lash.message-text",
                    "&a&l🐸 &2Les grenouilles ont attrapé &a{count} &2cultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));

                if (totalCombos > 0) {
                    String comboSuffix = plugin.getConfig().getString("frog-tongue-lash.combo-suffix",
                        " &7(&a{combos} combos&7)");
                    comboSuffix = comboSuffix.replace("{combos}", String.valueOf(totalCombos));
                    message += comboSuffix;
                }

                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_FROG_EAT, 1.0f, 1.2f);
            }
        });

        animation.start();
    }

    private boolean isMatureCrop(Block block) {
        Material type = block.getType();

        if (!CROPS.contains(type)) return false;
        if (NO_AGE_CROPS.contains(type)) return true;

        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }

        return true;
    }
}
