package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation du Phantom Géant
 *
 * Un Phantom géant (scale x2) apparaît et survole le champ
 * en effectuant des passes rasantes et des piqués pour récolter.
 *
 * Mécaniques:
 * - Vol en figure-8 avec passes rasantes
 * - Piqués d'attaque sur les cultures
 * - Traînée d'ombre et de particules d'âme
 * - Cri strident lors des attaques
 */
public class EnderDragonBreathAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final Player player;
    private final World world;

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

    // Paramètres de l'animation
    private final double phantomScale;
    private final int flightDuration;
    private final double spiralRadius;
    private final double spiralSpeed;
    private final int breathInterval;
    private final double breathRadius;
    private final int breathDuration;
    private final boolean showParticles;
    private final boolean clientSideOnly;

    // État de l'animation
    private Phantom phantom;
    private double flightAngle = 0;
    private int ticksElapsed = 0;
    private int totalCropsHarvested = 0;
    private int totalBreathClouds = 0;
    private boolean isDiving = false;
    private Location diveTarget = null;
    private int diveTicks = 0;

    // Tracking des blocs déjà récoltés
    private final Set<String> harvestedBlocks = new HashSet<>();

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onBreathCloud;
    private BiConsumer<Integer, Integer> onFinish;

    private final Random random = new Random();

    // Couleurs pour particules
    private static final Color PHANTOM_DARK = Color.fromRGB(20, 20, 40);
    private static final Color PHANTOM_PURPLE = Color.fromRGB(100, 50, 150);

    public EnderDragonBreathAnimation(RinaEnchantsPlugin plugin, Location startLocation, Player player,
                                       double dragonScale, int flightDuration, double spiralRadius,
                                       double spiralSpeed, int breathInterval, double breathRadius,
                                       int breathDuration, boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.player = player;
        this.world = startLocation.getWorld();

        // Scale x2 par rapport au paramètre original
        this.phantomScale = dragonScale * 5.0; // Phantom de base est petit, on multiplie plus
        this.flightDuration = flightDuration;
        this.spiralRadius = spiralRadius;
        this.spiralSpeed = spiralSpeed;
        this.breathInterval = breathInterval;
        this.breathRadius = breathRadius;
        this.breathDuration = breathDuration;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnBreathCloud(Consumer<Integer> callback) {
        this.onBreathCloud = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        // Spawn le phantom au-dessus du point de départ
        Location spawnLoc = startLocation.clone().add(0, 8, 0);

        try {
            phantom = (Phantom) world.spawnEntity(spawnLoc, EntityType.PHANTOM);
            phantom.setSilent(false);
            phantom.setInvulnerable(true);
            phantom.setGravity(false);
            phantom.setCollidable(false);
            phantom.setAI(false);
            phantom.setSize(4); // Taille du phantom (affecte la hitbox et l'apparence)

            // ═══════════════════════════════════════════════════════════
            // APPLIQUER L'ÉCHELLE x2 (Phantom géant)
            // ═══════════════════════════════════════════════════════════
            try {
                AttributeInstance scaleAttr = phantom.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) {
                    scaleAttr.setBaseValue(phantomScale);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e[Phantom] Attribut SCALE non disponible");
            }

            // Marquer l'entité pour cleanup
            plugin.markAsEnchantEntity(phantom);

            // Rendre client-side si configuré
            if (clientSideOnly) {
                plugin.makeEntityClientSide(phantom, player);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("§e[Phantom] Impossible de spawn le Phantom: " + e.getMessage());
            return;
        }

        // Jouer le son d'apparition
        player.playSound(startLocation, Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.7f);

        // Effet d'apparition
        if (showParticles) {
            for (int i = 0; i < 20; i++) {
                double ox = (random.nextDouble() - 0.5) * 3;
                double oy = random.nextDouble() * 2;
                double oz = (random.nextDouble() - 0.5) * 3;
                player.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0);
            }
            player.spawnParticle(Particle.SOUL, spawnLoc, 10, 1, 1, 1, 0.05);
        }

        // Démarrer l'animation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (phantom == null || phantom.isDead() || !player.isOnline()) {
                    cleanup();
                    cancel();
                    return;
                }

                ticksElapsed++;

                if (isDiving) {
                    // Animation de piqué
                    updateDiveMovement();
                } else {
                    // Vol normal en figure-8
                    updateFlightMovement();

                    // Vérifier si c'est le moment de plonger
                    if (ticksElapsed % breathInterval == 0) {
                        startDive();
                    }
                }

                // Particules de traînée
                if (showParticles && ticksElapsed % 2 == 0) {
                    spawnTrailParticles();
                }

                // Vérifier la fin de l'animation
                if (ticksElapsed >= flightDuration) {
                    performFinalSweep();
                    cleanup();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Vol en figure-8 dynamique
     */
    private void updateFlightMovement() {
        if (phantom == null || phantom.isDead()) return;

        flightAngle += spiralSpeed;

        // Figure-8 (lemniscate de Bernoulli)
        double t = flightAngle;
        double scale = spiralRadius;

        // Paramètres de la figure-8
        double x = scale * Math.sin(t);
        double z = scale * Math.sin(t) * Math.cos(t);

        // Hauteur variable avec oscillation
        double heightBase = 6.0;
        double heightWave = Math.sin(t * 0.5) * 2.0;
        double y = heightBase + heightWave;

        Location newLoc = startLocation.clone().add(x, y, z);

        // Calculer la direction de vol
        Location currentLoc = phantom.getLocation();
        Vector direction = newLoc.toVector().subtract(currentLoc.toVector());

        if (direction.lengthSquared() > 0.01) {
            direction.normalize();

            // Calculer yaw et pitch
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            float pitch = (float) Math.toDegrees(-Math.asin(direction.getY()));

            newLoc.setYaw(yaw);
            newLoc.setPitch(pitch);
        }

        // Mouvement fluide
        phantom.teleport(newLoc);
    }

    /**
     * Démarre un piqué d'attaque
     */
    private void startDive() {
        isDiving = true;
        diveTicks = 0;

        // Trouver une cible (culture mature ou position aléatoire)
        diveTarget = findDiveTarget();

        // Son de début de piqué
        player.playSound(phantom.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.8f);

        totalBreathClouds++;
        if (onBreathCloud != null) {
            onBreathCloud.accept(totalBreathClouds);
        }
    }

    /**
     * Trouve une cible pour le piqué
     */
    private Location findDiveTarget() {
        // Chercher une culture à proximité
        int searchRadius = (int) spiralRadius;

        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = random.nextInt(searchRadius * 2) - searchRadius;
            int dz = random.nextInt(searchRadius * 2) - searchRadius;

            Location checkLoc = startLocation.clone().add(dx, 0, dz);

            // Chercher une culture à cette position
            for (int dy = -2; dy <= 2; dy++) {
                Location cropLoc = checkLoc.clone().add(0, dy, 0);
                if (isMatureCrop(cropLoc.getBlock())) {
                    return cropLoc;
                }
            }
        }

        // Position aléatoire si pas de culture trouvée
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = random.nextDouble() * spiralRadius;
        return startLocation.clone().add(
            Math.cos(angle) * dist,
            0,
            Math.sin(angle) * dist
        );
    }

    /**
     * Animation du piqué
     */
    private void updateDiveMovement() {
        diveTicks++;

        Location currentLoc = phantom.getLocation();
        Location targetLoc = diveTarget.clone().add(0, 1, 0);

        // Phase 1: Descente rapide (20 ticks)
        if (diveTicks <= 20) {
            double progress = diveTicks / 20.0;
            double easeProgress = 1 - Math.pow(1 - progress, 3); // Ease out cubic

            // Interpolation vers la cible
            double x = currentLoc.getX() + (targetLoc.getX() - currentLoc.getX()) * 0.2;
            double z = currentLoc.getZ() + (targetLoc.getZ() - currentLoc.getZ()) * 0.2;
            double y = currentLoc.getY() - 0.4; // Descente rapide

            Location newLoc = new Location(world, x, Math.max(y, targetLoc.getY()), z);

            // Direction vers le bas
            Vector dir = targetLoc.toVector().subtract(newLoc.toVector());
            if (dir.lengthSquared() > 0) {
                dir.normalize();
                newLoc.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
                newLoc.setPitch(45); // Plongeon
            }

            phantom.teleport(newLoc);

            // Effet de vitesse
            if (showParticles && diveTicks % 2 == 0) {
                player.spawnParticle(Particle.SOUL, currentLoc, 3, 0.3, 0.3, 0.3, 0.02);
            }
        }
        // Phase 2: Impact et récolte
        else if (diveTicks == 21) {
            // Récolter autour du point d'impact
            harvestInRadius(diveTarget, breathRadius);

            // Effet d'impact
            if (showParticles) {
                Location impactLoc = diveTarget.clone().add(0, 0.5, 0);

                // Onde de choc circulaire
                for (int i = 0; i < 16; i++) {
                    double angle = (Math.PI * 2 / 16) * i;
                    for (int r = 1; r <= (int) breathRadius; r++) {
                        double px = Math.cos(angle) * r;
                        double pz = Math.sin(angle) * r;
                        player.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            impactLoc.clone().add(px, 0, pz), 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }

                // Particules centrales
                player.spawnParticle(Particle.DRAGON_BREATH, impactLoc, 15, breathRadius * 0.3, 0.2, breathRadius * 0.3, 0.02);
                player.spawnParticle(Particle.PORTAL, impactLoc, 20, breathRadius * 0.5, 0.5, breathRadius * 0.5, 0.5);
            }

            // Son d'impact
            player.playSound(diveTarget, Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.6f);
            player.playSound(diveTarget, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.3f, 1.5f);
        }
        // Phase 3: Remontée (25 ticks)
        else if (diveTicks <= 45) {
            double y = phantom.getLocation().getY() + 0.35;
            Location newLoc = phantom.getLocation().clone();
            newLoc.setY(y);
            newLoc.setPitch(-30); // Remontée

            phantom.teleport(newLoc);
        }
        // Fin du piqué
        else {
            isDiving = false;
            diveTarget = null;
        }
    }

    private void harvestInRadius(Location center, double radius) {
        int radiusInt = (int) Math.ceil(radius);

        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;

                for (int dy = -1; dy <= 2; dy++) {
                    Location cropLoc = center.clone().add(dx, dy, dz);
                    String key = cropLoc.getBlockX() + ":" + cropLoc.getBlockY() + ":" + cropLoc.getBlockZ();

                    if (harvestedBlocks.contains(key)) continue;

                    Block block = cropLoc.getBlock();
                    if (isMatureCrop(block)) {
                        harvestedBlocks.add(key);
                        totalCropsHarvested++;

                        if (onCropHit != null) {
                            onCropHit.accept(cropLoc);
                        }

                        if (showParticles) {
                            player.spawnParticle(Particle.SOUL,
                                cropLoc.clone().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1, 0.02);
                        }
                    }
                }
            }
        }
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

    /**
     * Particules de traînée sombre
     */
    private void spawnTrailParticles() {
        Location phantomLoc = phantom.getLocation();

        // Traînée d'âme
        for (int i = 0; i < 2; i++) {
            double ox = (random.nextDouble() - 0.5) * 1.5;
            double oy = (random.nextDouble() - 0.5) * 0.5;
            double oz = (random.nextDouble() - 0.5) * 1.5;

            player.spawnParticle(Particle.SOUL, phantomLoc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0);
        }

        // Particules de portail
        player.spawnParticle(Particle.PORTAL, phantomLoc, 2, 0.8, 0.3, 0.8, 0);

        // Dust sombre
        if (ticksElapsed % 4 == 0) {
            Particle.DustOptions dust = new Particle.DustOptions(PHANTOM_DARK, 1.5f);
            player.spawnParticle(Particle.DUST, phantomLoc.clone().add(0, -0.5, 0), 2, 0.5, 0.2, 0.5, 0, dust);
        }
    }

    /**
     * Attaque finale en balayage
     */
    private void performFinalSweep() {
        if (showParticles) {
            Location phantomLoc = phantom.getLocation();

            // Explosion de particules d'âme
            for (int i = 0; i < 30; i++) {
                double ox = (random.nextDouble() - 0.5) * 4;
                double oy = (random.nextDouble() - 0.5) * 2;
                double oz = (random.nextDouble() - 0.5) * 4;

                player.spawnParticle(Particle.SOUL_FIRE_FLAME, phantomLoc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0.05);
                player.spawnParticle(Particle.PORTAL, phantomLoc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0.3);
            }

            // Balayage final au sol
            double finalRadius = breathRadius * 2;
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = random.nextDouble() * finalRadius;
                double x = Math.cos(angle) * dist;
                double z = Math.sin(angle) * dist;

                player.spawnParticle(Particle.SOUL,
                    startLocation.clone().add(x, 0.5, z), 1, 0.1, 0.2, 0.1, 0.01);
            }

            // Récolte finale
            harvestInRadius(startLocation, finalRadius);
        }

        // Son de départ
        player.playSound(phantom.getLocation(), Sound.ENTITY_PHANTOM_DEATH, 0.8f, 1.2f);
    }

    private void cleanup() {
        if (phantom != null && !phantom.isDead()) {
            // Effet de disparition
            if (showParticles && player.isOnline()) {
                Location loc = phantom.getLocation();
                player.spawnParticle(Particle.SOUL, loc, 15, 1, 0.5, 1, 0.1);
                player.spawnParticle(Particle.PORTAL, loc, 20, 1, 1, 1, 0.5);
            }

            phantom.remove();
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalBreathClouds);
        }
    }
}
