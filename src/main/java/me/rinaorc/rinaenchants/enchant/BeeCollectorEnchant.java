package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.BeeAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Bee Collector / Apiculteur
 * 
 * Optimisé pour serveurs 500+ joueurs:
 * - Entités visibles uniquement par le joueur qui proc (client-side)
 * - Système de tracking pour éviter les proc en cascade
 * - Distribution aléatoire des cultures entre les abeilles
 */
public class BeeCollectorEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();
    private final Random random = new Random();

    public BeeCollectorEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ BeeCollector: " + CROPS.size() + " types de cultures chargés!");
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
    
    /**
     * Plus besoin de listener onBlockBreak - la vérification se fait directement
     * dans onEnchantProc via isEntityBreakingLocation()
     */

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("bee-collector.enchant-id", "bee_collector");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel, 
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {
        
        boolean debug = plugin.getConfig().getBoolean("debug", false);

        if (debug) {
            plugin.getLogger().info("§a[BeeCollector] onEnchantProc appelé! Joueur: " + player.getName() + ", Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE: Empêche les proc récursifs
        // Si une entité (abeille/panda/allay) casse cette culture, on ignore
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§c[BeeCollector] Bloqué - isEntityBreakingLocation=true");
            }
            return;
        }

        // Récupérer les paramètres de config
        int baseRadius = plugin.getConfig().getInt("bee-collector.radius", 3);
        int baseBeeCount = plugin.getConfig().getInt("bee-collector.bee-count", 3);
        int maxCrops = plugin.getConfig().getInt("bee-collector.max-crops", 10);
        double beeSpeed = plugin.getConfig().getDouble("bee-collector.bee-speed", 1.0);
        boolean showParticles = plugin.getConfig().getBoolean("bee-collector.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("bee-collector.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("bee-collector.client-side-only", true);
        
        // Bonus basé sur le niveau de l'enchant (amélioré pour récolter plus)
        int radius = baseRadius + (int)(enchantLevel / 2);
        int beeCount = baseBeeCount + (int)(enchantLevel / 2);
        int maxCropsToHarvest = maxCrops + (int)(enchantLevel * 3);

        if (debug) {
            plugin.getLogger().info("§a[BeeCollector] Config: radius=" + baseRadius + "+" + (int)(enchantLevel / 2) +
                "=" + radius + ", maxCrops=" + maxCrops + "+" + (int)(enchantLevel * 3) + "=" + maxCropsToHarvest);
        }

        // Trouver les cultures matures à proximité
        List<Location> matureCrops = findMatureCrops(cropLocation, radius, maxCropsToHarvest);

        if (debug) {
            plugin.getLogger().info("§a[BeeCollector] Cultures trouvées: " + matureCrops.size() + "/" + maxCropsToHarvest + " (rayon: " + radius + ")");
        }

        if (matureCrops.isEmpty()) {
            return; // Pas de cultures à récolter
        }

        World world = cropLocation.getWorld();
        if (world == null) {
            return;
        }
        
        // Jouer le son d'abeilles (seulement pour le joueur)
        if (playSound) {
            player.playSound(player.getLocation(), Sound.ENTITY_BEE_LOOP, 0.8f, 1.2f);
        }
        
        // ═══════════════════════════════════════════════════════════
        // DISTRIBUTION VRAIMENT ALÉATOIRE des cultures entre les abeilles
        // Chaque abeille a son propre chemin unique et aléatoire
        // ═══════════════════════════════════════════════════════════
        int actualBeeCount = Math.min(beeCount, Math.max(1, matureCrops.size()));
        
        // Créer des listes de cultures pour chaque abeille
        List<List<Location>> beePaths = new ArrayList<>();
        for (int i = 0; i < actualBeeCount; i++) {
            beePaths.add(new ArrayList<>());
        }
        
        // Mélanger la liste des cultures
        List<Location> shuffledCrops = new ArrayList<>(matureCrops);
        Collections.shuffle(shuffledCrops, random);
        
        // Distribution aléatoire: chaque culture va à une abeille aléatoire
        for (Location crop : shuffledCrops) {
            int beeIndex = random.nextInt(actualBeeCount);
            beePaths.get(beeIndex).add(crop);
        }
        
        // Mélanger l'ordre de visite pour chaque abeille
        for (List<Location> path : beePaths) {
            Collections.shuffle(path, random);
        }
        
        // Spawner les abeilles
        for (int i = 0; i < actualBeeCount; i++) {
            final int beeIndex = i;
            List<Location> beeCrops = beePaths.get(i);
            
            // Skip si cette abeille n'a pas de cultures assignées
            if (beeCrops.isEmpty()) continue;

            // Position de spawn ALÉATOIRE autour du joueur
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 1.5 + random.nextDouble() * 2;
            Location beeSpawn = player.getLocation().clone().add(
                Math.cos(angle) * distance,
                1.5 + random.nextDouble() * 1.5,
                Math.sin(angle) * distance
            );

            // Délai aléatoire pour chaque abeille
            long delay = (long)(random.nextInt(10) + beeIndex * 2);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Créer l'animation de l'abeille
                BeeAnimation animation = new BeeAnimation(plugin, beeSpawn, beeCrops, showParticles, beeIndex, player, clientSideOnly, beeSpeed);
                
                // Callback quand l'abeille atteint une culture
                animation.setOnCropReached((cropLoc) -> {
                    Block block = cropLoc.getBlock();
                    if (CROPS.contains(block.getType())) {
                        plugin.safeBreakCrop(player, cropLoc, "bee-collector");
                    }
                    
                    if (showParticles) {
                        // Particules visibles uniquement pour le joueur
                        player.spawnParticle(Particle.HAPPY_VILLAGER, 
                            cropLoc.clone().add(0.5, 0.5, 0.5), 
                            8, 0.3, 0.3, 0.3, 0);
                    }
                    
                    if (playSound) {
                        player.playSound(cropLoc, Sound.BLOCK_CROP_BREAK, 0.5f, 1.2f);
                    }
                });
                
                animation.start();
                
            }, delay);
        }

        // Message au joueur
        if (plugin.getConfig().getBoolean("bee-collector.message", true)) {
            String message = plugin.getConfig().getString("bee-collector.message-text", 
                "&e&l🐝 &6Vos abeilles récoltent &e{count} &6cultures!");
            message = message.replace("{count}", String.valueOf(matureCrops.size()));
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }
    }

    /**
     * Trouve les cultures dans un rayon donné
     *
     * OPTIMISATION: Au lieu de créer une liste de tous les offsets et la mélanger
     * (coûteux en mémoire pour les grands rayons), on utilise un échantillonnage
     * aléatoire direct avec un parcours en spirale pour une meilleure distribution.
     */
    private List<Location> findMatureCrops(Location center, int radius, int maxCount) {
        List<Location> crops = new ArrayList<>(maxCount);
        World world = center.getWorld();

        if (world == null) return crops;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Limiter le rayon pour éviter les calculs excessifs
        int effectiveRadius = Math.min(radius, 15);

        // Nombre total de blocs à vérifier (approximatif)
        int totalBlocks = (2 * effectiveRadius + 1) * (2 * effectiveRadius + 1) * 5;

        // Si on cherche moins de cultures qu'il n'y a de blocs, utiliser l'échantillonnage aléatoire
        if (maxCount < totalBlocks / 4) {
            // Échantillonnage aléatoire: essayer des positions aléatoires
            int attempts = Math.min(maxCount * 8, totalBlocks);
            for (int i = 0; i < attempts && crops.size() < maxCount; i++) {
                int x = random.nextInt(2 * effectiveRadius + 1) - effectiveRadius;
                int y = random.nextInt(5) - 2;
                int z = random.nextInt(2 * effectiveRadius + 1) - effectiveRadius;

                if (x == 0 && y == 0 && z == 0) continue;

                Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                if (isMatureCrop(block)) {
                    Location loc = block.getLocation();
                    // Éviter les doublons
                    if (!crops.contains(loc)) {
                        crops.add(loc);
                    }
                }
            }
        } else {
            // Parcours direct si on veut beaucoup de cultures
            for (int x = -effectiveRadius; x <= effectiveRadius && crops.size() < maxCount; x++) {
                for (int y = -2; y <= 2 && crops.size() < maxCount; y++) {
                    for (int z = -effectiveRadius; z <= effectiveRadius && crops.size() < maxCount; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                        if (isMatureCrop(block)) {
                            crops.add(block.getLocation());
                        }
                    }
                }
            }
            // Mélanger le résultat pour une distribution aléatoire
            Collections.shuffle(crops, random);
        }

        return crops;
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
