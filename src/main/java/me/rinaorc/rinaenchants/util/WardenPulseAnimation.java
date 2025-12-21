package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation du Warden avec Pulses Soniques
 *
 * WARDEN PULSE / ONDE DU WARDEN
 *
 * Le Warden émerge du sol et émet des ondes soniques concentriques
 * qui récoltent les cultures en vagues successives.
 *
 * Caractéristiques:
 * - Animation d'émergence du Warden
 * - Pulses soniques concentriques (cercles qui s'étendent)
 * - Système de résonance: zones touchées 2+ fois = bonus
 * - Effets visuels sculk et sonic boom
 * - Client-side pour optimisation serveur 500+ joueurs
 */
public class WardenPulseAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location centerLocation;
    private final Player owner;
    private final int pulseCount;
    private final int pulseInterval;
    private final int maxPulseRadius;
    private final int pulseExpandSpeed;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Random random;

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onResonance;
    private BiConsumer<Integer, Integer> onFinish;

    // État
    private Warden wardenEntity;
    private int totalCropsHarvested = 0;
    private int totalResonances = 0;

    // Tracking des zones pour la résonance
    private final Map<String, Integer> zoneHitCount = new HashMap<>();
    private static final int RESONANCE_THRESHOLD = 2;

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

    // Couleurs pour les particules de pulse
    private static final Color SCULK_BLUE = Color.fromRGB(0, 150, 180);
    private static final Color SCULK_DARK = Color.fromRGB(10, 40, 50);
    private static final Color RESONANCE_CYAN = Color.fromRGB(0, 255, 255);

    public WardenPulseAnimation(RinaEnchantsPlugin plugin, Location centerLocation, Player owner,
                                int pulseCount, int pulseInterval, int maxPulseRadius,
                                int pulseExpandSpeed, boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.centerLocation = centerLocation.clone();
        this.owner = owner;
        this.pulseCount = pulseCount;
        this.pulseInterval = pulseInterval;
        this.maxPulseRadius = maxPulseRadius;
        this.pulseExpandSpeed = pulseExpandSpeed;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.random = new Random();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnResonance(Consumer<Integer> callback) {
        this.onResonance = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // PHASE 1: ANIMATION D'ÉMERGENCE DU WARDEN
        // ═══════════════════════════════════════════════════════════

        Location spawnLoc = centerLocation.clone();

        // Effet d'émergence (particules sculk remontant du sol)
        if (showParticles) {
            for (int i = 0; i < 15; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = random.nextDouble() * 1.5;
                Location particleLoc = spawnLoc.clone().add(
                    Math.cos(angle) * dist,
                    -0.5 + random.nextDouble() * 2,
                    Math.sin(angle) * dist
                );
                owner.spawnParticle(Particle.SCULK_SOUL, particleLoc, 1, 0, 0.5, 0, 0.05);
            }
            owner.spawnParticle(Particle.SCULK_CHARGE_POP, spawnLoc, 10, 0.5, 0.5, 0.5, 0.1);
        }

        // Son d'émergence
        owner.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, 0.8f, 1.0f);

        try {
            wardenEntity = (Warden) world.spawnEntity(spawnLoc, EntityType.WARDEN);
            wardenEntity.setInvulnerable(true);
            wardenEntity.setSilent(false);
            wardenEntity.setAI(false);
            wardenEntity.setGravity(false);
            wardenEntity.setCollidable(false);
            wardenEntity.setRemoveWhenFarAway(false);

            // Marquer l'entité pour cleanup
            plugin.markAsEnchantEntity(wardenEntity);

            // Client-side
            if (clientSideOnly) {
                plugin.makeEntityClientSide(wardenEntity, owner);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn le Warden: " + e.getMessage());
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // PHASE 2: DÉMARRER LES PULSES APRÈS L'ÉMERGENCE
        // ═══════════════════════════════════════════════════════════

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (wardenEntity == null || wardenEntity.isDead()) {
                finishAnimation();
                return;
            }

            // Démarrer la séquence de pulses
            new PulseSequenceTask().runTaskTimer(plugin, 0L, 1L);

        }, 20L); // 1 seconde d'émergence
    }

    /**
     * Task qui gère la séquence de pulses
     */
    private class PulseSequenceTask extends BukkitRunnable {

        private int ticksAlive = 0;
        private int pulsesEmitted = 0;
        private int ticksSinceLastPulse = 0;
        private final List<ActivePulse> activePulses = new ArrayList<>();

        @Override
        public void run() {
            ticksAlive++;
            ticksSinceLastPulse++;

            // Vérifier si le propriétaire est toujours en ligne
            if (!owner.isOnline()) {
                cleanup();
                cancel();
                return;
            }

            // Vérifier si le Warden existe
            if (wardenEntity == null || wardenEntity.isDead() || !wardenEntity.isValid()) {
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

            // ═══════════════════════════════════════════════════════════
            // ÉMETTRE UN NOUVEAU PULSE
            // ═══════════════════════════════════════════════════════════

            if (pulsesEmitted < pulseCount && ticksSinceLastPulse >= pulseInterval) {
                ticksSinceLastPulse = 0;
                pulsesEmitted++;

                // Créer un nouveau pulse
                ActivePulse newPulse = new ActivePulse(wardenEntity.getLocation().clone());
                activePulses.add(newPulse);

                // Effet de shriek du Warden
                if (showParticles) {
                    Location wardenLoc = wardenEntity.getLocation();
                    owner.spawnParticle(Particle.SONIC_BOOM, wardenLoc.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
                    owner.spawnParticle(Particle.SCULK_SOUL, wardenLoc, 8, 0.5, 1, 0.5, 0.1);
                }

                owner.playSound(wardenEntity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.2f);

                // Animation de "roar" visuel (particules autour du Warden)
                if (showParticles) {
                    for (int i = 0; i < 6; i++) {
                        double angle = (Math.PI * 2 / 6) * i;
                        Location ringLoc = wardenEntity.getLocation().clone().add(
                            Math.cos(angle) * 0.8, 1.5, Math.sin(angle) * 0.8
                        );
                        Particle.DustOptions dust = new Particle.DustOptions(SCULK_BLUE, 1.5f);
                        owner.spawnParticle(Particle.DUST, ringLoc, 2, 0.1, 0.1, 0.1, 0, dust);
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // METTRE À JOUR TOUS LES PULSES ACTIFS
            // ═══════════════════════════════════════════════════════════

            Iterator<ActivePulse> iterator = activePulses.iterator();
            while (iterator.hasNext()) {
                ActivePulse pulse = iterator.next();
                pulse.update(world);

                if (pulse.isFinished()) {
                    iterator.remove();
                }
            }

            // ═══════════════════════════════════════════════════════════
            // AMBIANCE DU WARDEN
            // ═══════════════════════════════════════════════════════════

            if (ticksAlive % 40 == 0 && showParticles) {
                // Particules d'aura sculk autour du Warden
                Location wardenLoc = wardenEntity.getLocation();
                for (int i = 0; i < 3; i++) {
                    Location auraLoc = wardenLoc.clone().add(
                        (random.nextDouble() - 0.5) * 1.5,
                        random.nextDouble() * 2.5,
                        (random.nextDouble() - 0.5) * 1.5
                    );
                    owner.spawnParticle(Particle.SCULK_SOUL, auraLoc, 1, 0, 0, 0, 0.02);
                }
            }

            // Heartbeat du Warden
            if (ticksAlive % 40 == 0) {
                owner.playSound(wardenEntity.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 1.0f);
            }

            // ═══════════════════════════════════════════════════════════
            // FIN DE L'ANIMATION
            // ═══════════════════════════════════════════════════════════

            if (pulsesEmitted >= pulseCount && activePulses.isEmpty()) {
                cleanup();
                cancel();
            }
        }

        /**
         * Représente un pulse sonique en expansion
         */
        private class ActivePulse {

            private final Location origin;
            private double currentRadius = 0;
            private int tick = 0;
            private final Set<String> harvestedInThisPulse = new HashSet<>();

            ActivePulse(Location origin) {
                this.origin = origin.clone();
            }

            void update(World world) {
                tick++;

                // Expansion du pulse
                double previousRadius = currentRadius;
                currentRadius += (double) pulseExpandSpeed / 20.0; // blocks per tick

                if (currentRadius > maxPulseRadius) {
                    return; // Pulse terminé
                }

                // ═══════════════════════════════════════════════════════════
                // DESSINER LE CERCLE DU PULSE
                // ═══════════════════════════════════════════════════════════

                if (showParticles) {
                    // Nombre de points sur le cercle (plus grand rayon = plus de points)
                    int points = (int) (currentRadius * 4);
                    points = Math.max(6, Math.min(points, 32));

                    Particle.DustOptions pulseDust = new Particle.DustOptions(SCULK_BLUE, 1.0f);
                    Particle.DustOptions pulseGlow = new Particle.DustOptions(SCULK_DARK, 1.5f);

                    for (int i = 0; i < points; i++) {
                        double angle = (Math.PI * 2 / points) * i;
                        double x = Math.cos(angle) * currentRadius;
                        double z = Math.sin(angle) * currentRadius;

                        // Trouver la hauteur du sol
                        Location circleLoc = origin.clone().add(x, 0, z);
                        circleLoc = findGroundLevel(circleLoc, world);

                        if (circleLoc != null) {
                            circleLoc.add(0, 0.1, 0); // Légèrement au-dessus du sol

                            // Particule principale
                            owner.spawnParticle(Particle.DUST, circleLoc, 1, 0, 0, 0, 0, pulseDust);

                            // Effet de traînée (particules moins fréquentes)
                            if (i % 4 == 0) {
                                owner.spawnParticle(Particle.DUST, circleLoc, 1, 0.1, 0.1, 0.1, 0, pulseGlow);
                                owner.spawnParticle(Particle.SCULK_SOUL, circleLoc, 1, 0.1, 0.2, 0.1, 0.01);
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // RÉCOLTER LES CULTURES DANS L'ANNEAU DU PULSE
                // ═══════════════════════════════════════════════════════════

                // On récolte seulement les blocs dans l'anneau entre previousRadius et currentRadius
                int minR = (int) Math.floor(previousRadius);
                int maxR = (int) Math.ceil(currentRadius);

                int cx = origin.getBlockX();
                int cy = origin.getBlockY();
                int cz = origin.getBlockZ();

                for (int x = -maxR; x <= maxR; x++) {
                    for (int z = -maxR; z <= maxR; z++) {
                        double distSq = x * x + z * z;
                        double dist = Math.sqrt(distSq);

                        // Seulement les blocs dans l'anneau du pulse actuel
                        if (dist < previousRadius || dist > currentRadius) continue;

                        for (int y = -2; y <= 2; y++) {
                            Location blockLoc = new Location(world, cx + x, cy + y, cz + z);
                            String key = blockLoc.getBlockX() + ":" + blockLoc.getBlockY() + ":" + blockLoc.getBlockZ();

                            if (harvestedInThisPulse.contains(key)) continue;

                            Block block = blockLoc.getBlock();
                            if (isMatureCrop(block)) {
                                harvestedInThisPulse.add(key);
                                totalCropsHarvested++;

                                // Tracker pour la résonance
                                int hitCount = zoneHitCount.getOrDefault(key, 0) + 1;
                                zoneHitCount.put(key, hitCount);

                                // Résonance!
                                if (hitCount == RESONANCE_THRESHOLD) {
                                    triggerResonance(blockLoc);
                                }

                                // Callback
                                if (onCropHit != null) {
                                    onCropHit.accept(blockLoc);
                                }

                                // Effet de récolte
                                if (showParticles) {
                                    owner.spawnParticle(Particle.SCULK_CHARGE_POP, blockLoc.clone().add(0.5, 0.5, 0.5),
                                        2, 0.2, 0.2, 0.2, 0.05);
                                }
                            }
                        }
                    }
                }
            }

            boolean isFinished() {
                return currentRadius > maxPulseRadius;
            }

            /**
             * Trouve le niveau du sol à une position donnée
             */
            private Location findGroundLevel(Location loc, World world) {
                int startY = loc.getBlockY();
                for (int y = startY; y >= startY - 3; y--) {
                    Location checkLoc = new Location(world, loc.getBlockX(), y, loc.getBlockZ());
                    if (!checkLoc.getBlock().isPassable()) {
                        return checkLoc.clone().add(0.5, 1, 0.5);
                    }
                }
                for (int y = startY + 1; y <= startY + 3; y++) {
                    Location checkLoc = new Location(world, loc.getBlockX(), y, loc.getBlockZ());
                    if (!checkLoc.getBlock().isPassable()) {
                        return checkLoc.clone().add(0.5, 1, 0.5);
                    }
                }
                return loc;
            }
        }

        /**
         * Déclenche un effet de résonance
         */
        private void triggerResonance(Location loc) {
            totalResonances++;

            if (onResonance != null) {
                onResonance.accept(totalResonances);
            }

            // Effet visuel de résonance (plus spectaculaire)
            if (showParticles) {
                Location effectLoc = loc.clone().add(0.5, 0.5, 0.5);

                // Explosion de particules cyan
                Particle.DustOptions resonanceDust = new Particle.DustOptions(RESONANCE_CYAN, 2.0f);
                owner.spawnParticle(Particle.DUST, effectLoc, 10, 0.3, 0.3, 0.3, 0, resonanceDust);

                // Cercle de particules
                for (int i = 0; i < 6; i++) {
                    double angle = (Math.PI * 2 / 6) * i;
                    Location ringLoc = effectLoc.clone().add(Math.cos(angle) * 0.5, 0, Math.sin(angle) * 0.5);
                    owner.spawnParticle(Particle.END_ROD, ringLoc, 1, 0, 0.5, 0, 0.05);
                }

                // Particules sculk
                owner.spawnParticle(Particle.SCULK_SOUL, effectLoc, 5, 0.3, 0.5, 0.3, 0.1);
            }

            // Son de résonance
            owner.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.5f);
            owner.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
        }

        private void cleanup() {
            activePulses.clear();

            if (wardenEntity != null && !wardenEntity.isDead()) {
                Location loc = wardenEntity.getLocation();

                // Effet de disparition (retourne dans le sol)
                if (showParticles && owner.isOnline()) {
                    owner.spawnParticle(Particle.SCULK_SOUL, loc, 15, 0.5, 1, 0.5, 0.1);
                    owner.spawnParticle(Particle.SCULK_CHARGE_POP, loc, 12, 0.5, 0.5, 0.5, 0.1);

                    // Cercle de disparition
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        Location ringLoc = loc.clone().add(Math.cos(angle) * 1.5, 0.1, Math.sin(angle) * 1.5);
                        Particle.DustOptions dust = new Particle.DustOptions(SCULK_DARK, 2.0f);
                        owner.spawnParticle(Particle.DUST, ringLoc, 2, 0.1, 0, 0.1, 0, dust);
                    }
                }

                if (owner.isOnline()) {
                    owner.playSound(loc, Sound.ENTITY_WARDEN_DIG, 0.8f, 1.0f);
                }

                wardenEntity.remove();
            }

            finishAnimation();
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

    private void finishAnimation() {
        if (owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3f, 1.5f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalResonances);
        }
    }
}
