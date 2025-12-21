package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation de l'Usine à Golems
 *
 * GOLEM FACTORY / USINE À GOLEMS
 *
 * Spawne des Iron Golems miniatures qui patrouillent et récoltent.
 * Système de fusion: quand 2 golems se touchent, ils fusionnent
 * en un golem géant qui fait un ground slam massif!
 *
 * Caractéristiques:
 * - Golems miniatures (attribut SCALE)
 * - Patrouille aléatoire dans une zone
 * - Animation de swing arm lors de la récolte
 * - Fusion en golem géant
 * - Ground slam avec effet shockwave
 * - Client-side pour optimisation serveur 500+ joueurs
 */
public class GolemFactoryAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location centerLocation;
    private final Player owner;
    private final int golemCount;
    private final double golemScale;
    private final int patrolRadius;
    private final int duration;
    private final double mergeChance;
    private final int slamRadius;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Random random;

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onMerge;
    private BiConsumer<Integer, Integer> onFinish;

    // État
    private final List<GolemInstance> golems = new ArrayList<>();
    private int totalCropsHarvested = 0;
    private int totalMerges = 0;
    private boolean isFinished = false;

    // Set des cultures
    private static final Set<Material> CROPS = new HashSet<>();
    private static final Set<Material> NO_AGE_CROPS = new HashSet<>();

    static {
        CROPS.addAll(Arrays.asList(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
            Material.BAMBOO, Material.KELP, Material.KELP_PLANT,
            Material.TUBE_CORAL, Material.BUBBLE_CORAL, Material.BRAIN_CORAL,
            Material.FIRE_CORAL, Material.HORN_CORAL,
            Material.TUBE_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
            Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            Material.WARPED_ROOTS, Material.CRIMSON_ROOTS, Material.NETHER_SPROUTS,
            Material.TWISTING_VINES, Material.WEEPING_VINES,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.SUNFLOWER,
            Material.TALL_GRASS, Material.LARGE_FERN,
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING,
            Material.SPRUCE_SAPLING, Material.CHERRY_SAPLING, Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
            Material.SEA_PICKLE, Material.CHORUS_FLOWER, Material.CHORUS_PLANT
        ));

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

    // Couleurs pour les particules
    private static final Color IRON_GRAY = Color.fromRGB(180, 180, 180);
    private static final Color IRON_DARK = Color.fromRGB(100, 100, 100);
    private static final Color MERGE_GOLD = Color.fromRGB(255, 215, 0);

    /**
     * Instance d'un golem individuel
     */
    private class GolemInstance {
        IronGolem entity;
        Location targetLocation;
        int ticksSinceLastHarvest = 0;
        int ticksSinceDirectionChange = 0;
        boolean isMerging = false;
        boolean isGiant = false;
        double personalScale;

        GolemInstance(double scale) {
            this.personalScale = scale;
        }
    }

    public GolemFactoryAnimation(RinaEnchantsPlugin plugin, Location centerLocation, Player owner,
                                  int golemCount, double golemScale, int patrolRadius, int duration,
                                  double mergeChance, int slamRadius,
                                  boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.centerLocation = centerLocation.clone();
        this.owner = owner;
        this.golemCount = golemCount;
        this.golemScale = golemScale;
        this.patrolRadius = patrolRadius;
        this.duration = duration;
        this.mergeChance = mergeChance;
        this.slamRadius = slamRadius;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.random = new Random();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnMerge(Consumer<Integer> callback) {
        this.onMerge = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        // Spawner les mini-golems à des positions aléatoires dans un rayon de 20 blocs
        int spawnRadius = 20;

        for (int i = 0; i < golemCount; i++) {
            // Position aléatoire chaotique dans le rayon
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 3 + random.nextDouble() * (spawnRadius - 3); // Entre 3 et 20 blocs

            Location spawnLoc = centerLocation.clone().add(
                Math.cos(angle) * dist,
                0,
                Math.sin(angle) * dist
            );

            // Trouver le sol
            spawnLoc = findGroundLevel(spawnLoc, world);
            if (spawnLoc == null) continue;

            try {
                IronGolem golem = (IronGolem) world.spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
                golem.setInvulnerable(true);
                golem.setSilent(false);
                golem.setAI(false);
                golem.setGravity(true);
                golem.setCollidable(false);
                golem.setRemoveWhenFarAway(false);
                golem.setPlayerCreated(true);

                // ═══════════════════════════════════════════════════════════
                // APPLIQUER L'ÉCHELLE (mini-golem)
                // ═══════════════════════════════════════════════════════════
                try {
                    AttributeInstance scaleAttr = golem.getAttribute(Attribute.SCALE);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(golemScale);
                    }
                } catch (Exception e) {
                    // Attribut scale non disponible (version < 1.20.5)
                    plugin.getLogger().warning("§e[GolemFactory] Attribut SCALE non disponible");
                }

                // Marquer l'entité pour cleanup
                plugin.markAsEnchantEntity(golem);

                // Client-side
                if (clientSideOnly) {
                    plugin.makeEntityClientSide(golem, owner);
                }

                GolemInstance instance = new GolemInstance(golemScale);
                instance.entity = golem;
                instance.targetLocation = getRandomPatrolTarget();
                golems.add(instance);

                // Effet de spawn
                if (showParticles) {
                    Particle.DustOptions dust = new Particle.DustOptions(IRON_GRAY, 2.0f);
                    owner.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0, dust);
                    owner.spawnParticle(Particle.BLOCK, spawnLoc, 5, 0.3, 0.1, 0.3, 0.1,
                        Material.IRON_BLOCK.createBlockData());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn le Golem: " + e.getMessage());
            }
        }

        if (golems.isEmpty()) {
            return;
        }

        // Son de spawn
        owner.playSound(centerLocation, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.2f);
        owner.playSound(centerLocation, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);

        // Démarrer l'animation principale
        new GolemBehaviorTask().runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Task principale qui gère le comportement des golems
     */
    private class GolemBehaviorTask extends BukkitRunnable {

        private int ticksAlive = 0;
        private final Set<String> harvestedBlocks = new HashSet<>();
        private static final double MOVE_SPEED = 0.15; // Vitesse augmentée pour mouvement visible
        private static final double MERGE_DISTANCE = 2.5; // Distance augmentée pour éviter fusions trop faciles

        @Override
        public void run() {
            ticksAlive++;

            // Fin de la durée
            if (ticksAlive > duration || isFinished) {
                cleanup();
                cancel();
                return;
            }

            // Vérifier si le propriétaire est toujours en ligne
            if (!owner.isOnline()) {
                cleanup();
                cancel();
                return;
            }

            World world = owner.getWorld();
            if (world == null) {
                cleanup();
                cancel();
                return;
            }

            // Nettoyer les golems morts
            golems.removeIf(g -> g.entity == null || g.entity.isDead() || !g.entity.isValid());

            if (golems.isEmpty()) {
                cancel();
                finishAnimation();
                return;
            }

            // Mettre à jour chaque golem
            List<GolemInstance> golemsToRemove = new ArrayList<>();
            for (GolemInstance golem : golems) {
                if (golem.isMerging) continue;
                updateGolem(golem, world);
            }

            // Vérifier les fusions (seulement si au moins 2 golems non-géants)
            checkForMerges(world);

            // Sons ambient
            if (ticksAlive % 80 == 0 && !golems.isEmpty()) {
                GolemInstance randomGolem = golems.get(random.nextInt(golems.size()));
                if (randomGolem.entity != null && !randomGolem.entity.isDead()) {
                    owner.playSound(randomGolem.entity.getLocation(),
                        Sound.ENTITY_IRON_GOLEM_STEP, 0.4f, 1.2f + random.nextFloat() * 0.3f);
                }
            }
        }

        private void updateGolem(GolemInstance golem, World world) {
            golem.ticksSinceLastHarvest++;
            golem.ticksSinceDirectionChange++;

            Location currentLoc = golem.entity.getLocation();

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT VERS LA CIBLE
            // ═══════════════════════════════════════════════════════════

            if (golem.targetLocation == null || golem.ticksSinceDirectionChange > 40 ||
                currentLoc.distanceSquared(golem.targetLocation) < 2) {
                golem.targetLocation = getRandomPatrolTarget();
                golem.ticksSinceDirectionChange = 0;
            }

            // Calculer la direction vers la cible
            double dx = golem.targetLocation.getX() - currentLoc.getX();
            double dz = golem.targetLocation.getZ() - currentLoc.getZ();
            double distanceXZ = Math.sqrt(dx * dx + dz * dz);

            if (distanceXZ > 0.5) {
                // Normaliser la direction
                dx /= distanceXZ;
                dz /= distanceXZ;

                // Vitesse ajustée selon la taille
                double speed = golem.isGiant ? MOVE_SPEED * 0.7 : MOVE_SPEED;

                // Nouvelle position
                double newX = currentLoc.getX() + dx * speed;
                double newZ = currentLoc.getZ() + dz * speed;

                // Trouver le sol à la nouvelle position
                Location newLoc = new Location(world, newX, currentLoc.getY(), newZ);
                Location groundLoc = findGroundLevel(newLoc, world);

                if (groundLoc != null) {
                    // Calculer le yaw pour faire face à la direction de mouvement
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    groundLoc.setYaw(yaw);
                    groundLoc.setPitch(0);

                    golem.entity.teleport(groundLoc);
                }
            }

            // ═══════════════════════════════════════════════════════════
            // RÉCOLTER LES CULTURES À PROXIMITÉ
            // ═══════════════════════════════════════════════════════════

            if (golem.ticksSinceLastHarvest >= 5) {
                int harvestRadius = golem.isGiant ? 3 : 1;
                boolean harvested = harvestNearby(golem, world, harvestRadius);

                if (harvested) {
                    golem.ticksSinceLastHarvest = 0;

                    // Animation de swing (via particules car pas d'animation native simple)
                    if (showParticles) {
                        Location armLoc = currentLoc.clone().add(0, 1.0 * golem.personalScale, 0);
                        owner.spawnParticle(Particle.SWEEP_ATTACK, armLoc, 1, 0.5, 0.3, 0.5, 0);
                    }

                    owner.playSound(currentLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f,
                        golem.isGiant ? 0.7f : 1.3f);
                }
            }

            // ═══════════════════════════════════════════════════════════
            // EFFETS VISUELS DE MARCHE
            // ═══════════════════════════════════════════════════════════

            if (showParticles && ticksAlive % 8 == 0) {
                owner.spawnParticle(Particle.BLOCK, currentLoc, 1, 0.2, 0, 0.2, 0,
                    Material.IRON_BLOCK.createBlockData());
            }
        }

        private boolean harvestNearby(GolemInstance golem, World world, int radius) {
            Location loc = golem.entity.getLocation();
            int cx = loc.getBlockX();
            int cy = loc.getBlockY();
            int cz = loc.getBlockZ();
            boolean harvested = false;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location blockLoc = new Location(world, cx + x, cy + y, cz + z);
                        String key = blockLoc.getBlockX() + ":" + blockLoc.getBlockY() + ":" + blockLoc.getBlockZ();

                        if (harvestedBlocks.contains(key)) continue;

                        Block block = blockLoc.getBlock();
                        if (isMatureCrop(block)) {
                            harvestedBlocks.add(key);
                            totalCropsHarvested++;
                            harvested = true;

                            if (onCropHit != null) {
                                onCropHit.accept(blockLoc);
                            }

                            if (showParticles) {
                                owner.spawnParticle(Particle.HAPPY_VILLAGER,
                                    blockLoc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0);
                            }
                        }
                    }
                }
            }

            return harvested;
        }

        private void checkForMerges(World world) {
            // Chercher des paires de golems non-géants proches
            for (int i = 0; i < golems.size(); i++) {
                GolemInstance g1 = golems.get(i);
                if (g1.isGiant || g1.isMerging || g1.entity == null) continue;

                for (int j = i + 1; j < golems.size(); j++) {
                    GolemInstance g2 = golems.get(j);
                    if (g2.isGiant || g2.isMerging || g2.entity == null) continue;

                    double dist = g1.entity.getLocation().distance(g2.entity.getLocation());
                    if (dist < MERGE_DISTANCE) {
                        // Chance de fusion
                        if (random.nextDouble() * 100 < mergeChance) {
                            performMerge(g1, g2, world);
                            return; // Une seule fusion par tick
                        }
                    }
                }
            }
        }

        private void performMerge(GolemInstance g1, GolemInstance g2, World world) {
            g1.isMerging = true;
            g2.isMerging = true;
            totalMerges++;

            Location mergeLoc = g1.entity.getLocation().clone()
                .add(g2.entity.getLocation())
                .multiply(0.5);

            // Effet de fusion
            if (showParticles) {
                Particle.DustOptions goldDust = new Particle.DustOptions(MERGE_GOLD, 2.0f);
                owner.spawnParticle(Particle.DUST, mergeLoc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0, goldDust);
                owner.spawnParticle(Particle.TOTEM_OF_UNDYING, mergeLoc.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
            }

            owner.playSound(mergeLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);
            owner.playSound(mergeLoc, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.6f);

            // Supprimer les deux golems
            g1.entity.remove();
            g2.entity.remove();

            // Callback merge
            if (onMerge != null) {
                onMerge.accept(totalMerges);
            }

            // Créer le golem géant après un délai
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!owner.isOnline() || isFinished) return;

                spawnGiantGolem(mergeLoc, world);
            }, 10L);
        }

        private void spawnGiantGolem(Location loc, World world) {
            try {
                IronGolem giant = (IronGolem) world.spawnEntity(loc, EntityType.IRON_GOLEM);
                giant.setInvulnerable(true);
                giant.setSilent(false);
                giant.setAI(false);
                giant.setGravity(true);
                giant.setCollidable(false);
                giant.setRemoveWhenFarAway(false);
                giant.setPlayerCreated(true);

                // ═══════════════════════════════════════════════════════════
                // GOLEM GÉANT (scale x2)
                // ═══════════════════════════════════════════════════════════
                double giantScale = golemScale * 2.5;
                try {
                    AttributeInstance scaleAttr = giant.getAttribute(Attribute.SCALE);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(giantScale);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e[GolemFactory] Attribut SCALE non disponible pour le géant");
                }

                plugin.markAsEnchantEntity(giant);
                if (clientSideOnly) {
                    plugin.makeEntityClientSide(giant, owner);
                }

                GolemInstance giantInstance = new GolemInstance(giantScale);
                giantInstance.entity = giant;
                giantInstance.isGiant = true;
                giantInstance.targetLocation = getRandomPatrolTarget();
                golems.add(giantInstance);

                // Effet d'apparition du géant
                if (showParticles) {
                    owner.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1.5, 0), 2, 0.5, 0.5, 0.5, 0);
                    Particle.DustOptions ironDust = new Particle.DustOptions(IRON_GRAY, 3.0f);
                    owner.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0, ironDust);
                }

                owner.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.5f);

                // Programmer le ground slam
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (giantInstance.entity != null && !giantInstance.entity.isDead()) {
                        performGroundSlam(giantInstance, world);
                    }
                }, 40L);

            } catch (Exception e) {
                plugin.getLogger().warning("§e[GolemFactory] Impossible de spawn le golem géant: " + e.getMessage());
            }
        }

        private void performGroundSlam(GolemInstance giant, World world) {
            if (!owner.isOnline() || isFinished) return;

            Location slamLoc = giant.entity.getLocation();

            // ═══════════════════════════════════════════════════════════
            // GROUND SLAM - ONDE DE CHOC
            // ═══════════════════════════════════════════════════════════

            // Effet visuel du slam
            if (showParticles) {
                // Cercle d'impact
                for (int r = 1; r <= slamRadius; r++) {
                    final int radius = r;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!owner.isOnline()) return;

                        int points = radius * 4;
                        for (int i = 0; i < points; i++) {
                            double angle = (Math.PI * 2 / points) * i;
                            Location ringLoc = slamLoc.clone().add(
                                Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius
                            );
                            owner.spawnParticle(Particle.BLOCK, ringLoc, 2, 0.1, 0.1, 0.1, 0.1,
                                Material.DIRT.createBlockData());
                            owner.spawnParticle(Particle.DUST, ringLoc, 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(IRON_DARK, 1.5f));
                        }
                    }, (r - 1) * 2L);
                }

                // Impact central
                owner.spawnParticle(Particle.EXPLOSION, slamLoc, 1, 0, 0, 0, 0);
                owner.spawnParticle(Particle.BLOCK, slamLoc, 15, 0.5, 0.2, 0.5, 0.2,
                    Material.IRON_BLOCK.createBlockData());
            }

            // Sons
            owner.playSound(slamLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
            owner.playSound(slamLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            owner.playSound(slamLoc, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.8f);

            // Récolter dans la zone du slam
            int cx = slamLoc.getBlockX();
            int cy = slamLoc.getBlockY();
            int cz = slamLoc.getBlockZ();

            for (int x = -slamRadius; x <= slamRadius; x++) {
                for (int z = -slamRadius; z <= slamRadius; z++) {
                    if (x * x + z * z > slamRadius * slamRadius) continue;

                    for (int y = -2; y <= 2; y++) {
                        Location blockLoc = new Location(world, cx + x, cy + y, cz + z);
                        String key = blockLoc.getBlockX() + ":" + blockLoc.getBlockY() + ":" + blockLoc.getBlockZ();

                        if (harvestedBlocks.contains(key)) continue;

                        Block block = blockLoc.getBlock();
                        if (isMatureCrop(block)) {
                            harvestedBlocks.add(key);
                            totalCropsHarvested++;

                            if (onCropHit != null) {
                                onCropHit.accept(blockLoc);
                            }
                        }
                    }
                }
            }

            // Le géant disparaît après le slam
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (giant.entity != null && !giant.entity.isDead()) {
                    if (showParticles && owner.isOnline()) {
                        Location loc = giant.entity.getLocation();
                        owner.spawnParticle(Particle.POOF, loc.clone().add(0, 1, 0), 15, 0.5, 1, 0.5, 0.1);
                        owner.spawnParticle(Particle.BLOCK, loc, 10, 0.5, 0.5, 0.5, 0.1,
                            Material.IRON_BLOCK.createBlockData());
                    }
                    giant.entity.remove();
                    golems.remove(giant);
                }
            }, 30L);
        }

        private void cleanup() {
            for (GolemInstance golem : golems) {
                if (golem.entity != null && !golem.entity.isDead()) {
                    Location loc = golem.entity.getLocation();

                    if (showParticles && owner.isOnline()) {
                        owner.spawnParticle(Particle.POOF, loc.clone().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.1);
                        owner.spawnParticle(Particle.BLOCK, loc, 5, 0.3, 0.3, 0.3, 0.1,
                            Material.IRON_BLOCK.createBlockData());
                    }

                    golem.entity.remove();
                }
            }
            golems.clear();

            finishAnimation();
        }
    }

    private Location getRandomPatrolTarget() {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 3 + random.nextDouble() * 17; // Entre 3 et 20 blocs
        return centerLocation.clone().add(
            Math.cos(angle) * dist,
            0,
            Math.sin(angle) * dist
        );
    }

    private Location findGroundLevel(Location loc, World world) {
        double x = loc.getX();
        double z = loc.getZ();
        int startY = loc.getBlockY();

        // Chercher le sol en dessous
        for (int y = startY; y >= startY - 5; y--) {
            Block block = world.getBlockAt((int) Math.floor(x), y, (int) Math.floor(z));
            Block above = world.getBlockAt((int) Math.floor(x), y + 1, (int) Math.floor(z));
            if (block.getType().isSolid() && above.isPassable()) {
                return new Location(world, x, y + 1, z);
            }
        }

        // Chercher le sol au-dessus
        for (int y = startY + 1; y <= startY + 5; y++) {
            Block block = world.getBlockAt((int) Math.floor(x), y, (int) Math.floor(z));
            Block above = world.getBlockAt((int) Math.floor(x), y + 1, (int) Math.floor(z));
            if (block.getType().isSolid() && above.isPassable()) {
                return new Location(world, x, y + 1, z);
            }
        }

        // Si rien trouvé, retourner la position actuelle
        return new Location(world, x, loc.getY(), z);
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

    private void finishAnimation() {
        if (owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.5f, 1.5f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalMerges);
        }
    }
}
