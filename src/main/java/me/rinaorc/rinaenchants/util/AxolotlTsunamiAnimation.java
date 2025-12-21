package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation du Tsunami d'Axolotls
 *
 * AXOLOTL TSUNAMI / TSUNAMI D'AXOLOTLS
 *
 * Une vague d'axolotls déferle sur le champ dans une direction aléatoire!
 * Les axolotls surfent sur des particules d'eau et récoltent tout sur leur passage.
 *
 * Caractéristiques:
 * - Direction aléatoire à chaque proc
 * - Ligne d'axolotls qui avance en formation
 * - Particules de vague (eau, bulles, écume)
 * - Axolotl bleu (rare) = récolte bonus
 * - Certains axolotls "play dead" = effet bonus
 * - Retour de vague dans l'autre sens
 * - Client-side pour optimisation serveur 500+ joueurs
 */
public class AxolotlTsunamiAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location centerLocation;
    private final Player owner;
    private final int waveWidth;
    private final int waveDistance;
    private final int axolotlCount;
    private final double blueChance;
    private final double playDeadChance;
    private final boolean hasReturnWave;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Random random;

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onBlueBonus;
    private Consumer<Integer> onPlayDead;
    private BiConsumer<Integer, Integer> onFinish;

    // État
    private final List<AxolotlInstance> axolotls = new ArrayList<>();
    private int totalCropsHarvested = 0;
    private int totalBlueBonuses = 0;
    private int totalPlayDeads = 0;
    private Vector waveDirection;
    private boolean isReturnWave = false;

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
    private static final Color WAVE_BLUE = Color.fromRGB(30, 144, 255);
    private static final Color WAVE_CYAN = Color.fromRGB(0, 255, 255);
    private static final Color FOAM_WHITE = Color.fromRGB(240, 248, 255);

    /**
     * Instance d'un axolotl individuel
     */
    private class AxolotlInstance {
        Axolotl entity;
        Axolotl.Variant variant;
        double offsetFromCenter; // Position sur la ligne de la vague
        boolean isBlue;
        boolean isPlayingDead;
        int playDeadTicks = 0;

        AxolotlInstance(double offset, Axolotl.Variant variant) {
            this.offsetFromCenter = offset;
            this.variant = variant;
            this.isBlue = (variant == Axolotl.Variant.BLUE);
        }
    }

    public AxolotlTsunamiAnimation(RinaEnchantsPlugin plugin, Location centerLocation, Player owner,
                                    int waveWidth, int waveDistance, int axolotlCount,
                                    double blueChance, double playDeadChance, boolean hasReturnWave,
                                    boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.centerLocation = centerLocation.clone();
        this.owner = owner;
        this.waveWidth = waveWidth;
        this.waveDistance = waveDistance;
        this.axolotlCount = axolotlCount;
        this.blueChance = blueChance;
        this.playDeadChance = playDeadChance;
        this.hasReturnWave = hasReturnWave;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.random = new Random();

        // ═══════════════════════════════════════════════════════════
        // DIRECTION ALÉATOIRE
        // ═══════════════════════════════════════════════════════════
        double angle = random.nextDouble() * Math.PI * 2;
        this.waveDirection = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnBlueBonus(Consumer<Integer> callback) {
        this.onBlueBonus = callback;
    }

    public void setOnPlayDead(Consumer<Integer> callback) {
        this.onPlayDead = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        startWave(false);
    }

    private void startWave(boolean isReturn) {
        this.isReturnWave = isReturn;

        World world = centerLocation.getWorld();
        if (world == null) return;

        // Si c'est le retour, inverser la direction
        Vector currentDirection = isReturn ? waveDirection.clone().multiply(-1) : waveDirection.clone();

        // Calculer le vecteur perpendiculaire pour la ligne de la vague
        Vector perpendicular = new Vector(-currentDirection.getZ(), 0, currentDirection.getX()).normalize();

        // Position de départ de la vague
        Location waveStart = isReturn ?
            centerLocation.clone().add(waveDirection.clone().multiply(waveDistance)) :
            centerLocation.clone();

        // Spawner les axolotls en ligne
        double spacing = (double) waveWidth / (axolotlCount - 1);

        for (int i = 0; i < axolotlCount; i++) {
            double offset = -waveWidth / 2.0 + i * spacing;

            // Choisir la variante (rare: bleu)
            Axolotl.Variant variant;
            if (random.nextDouble() * 100 < blueChance) {
                variant = Axolotl.Variant.BLUE;
            } else {
                Axolotl.Variant[] commonVariants = {
                    Axolotl.Variant.LUCY,
                    Axolotl.Variant.WILD,
                    Axolotl.Variant.GOLD,
                    Axolotl.Variant.CYAN
                };
                variant = commonVariants[random.nextInt(commonVariants.length)];
            }

            Location spawnLoc = waveStart.clone().add(perpendicular.clone().multiply(offset));
            spawnLoc = findGroundLevel(spawnLoc, world);
            if (spawnLoc == null) continue;

            try {
                Axolotl axolotl = (Axolotl) world.spawnEntity(spawnLoc, EntityType.AXOLOTL);
                axolotl.setInvulnerable(true);
                axolotl.setSilent(false);
                axolotl.setAI(false);
                axolotl.setGravity(false);
                axolotl.setCollidable(false);
                axolotl.setRemoveWhenFarAway(false);
                axolotl.setVariant(variant);

                // Marquer l'entité pour cleanup
                plugin.markAsEnchantEntity(axolotl);

                // Client-side
                if (clientSideOnly) {
                    plugin.makeEntityClientSide(axolotl, owner);
                }

                AxolotlInstance instance = new AxolotlInstance(offset, variant);
                instance.entity = axolotl;
                axolotls.add(instance);

                // Effet de spawn
                if (showParticles) {
                    owner.spawnParticle(Particle.SPLASH, spawnLoc, 5, 0.3, 0.2, 0.3, 0.1);
                    if (instance.isBlue) {
                        // Effet spécial pour le bleu (rare!)
                        owner.spawnParticle(Particle.END_ROD, spawnLoc.clone().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn l'Axolotl: " + e.getMessage());
            }
        }

        if (axolotls.isEmpty()) {
            finishAnimation();
            return;
        }

        // Son de début de vague
        owner.playSound(waveStart, Sound.ENTITY_AXOLOTL_SPLASH, 1.0f, 0.8f);
        owner.playSound(waveStart, Sound.AMBIENT_UNDERWATER_ENTER, 0.8f, 1.2f);

        // Effet de vague au départ
        if (showParticles) {
            for (int i = 0; i < 10; i++) {
                Location waveLoc = waveStart.clone().add(
                    perpendicular.clone().multiply((random.nextDouble() - 0.5) * waveWidth)
                );
                owner.spawnParticle(Particle.SPLASH, waveLoc.clone().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0.1);
            }
        }

        // Démarrer l'animation de la vague
        new WaveTask(currentDirection, perpendicular).runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Task principale qui gère le mouvement de la vague
     */
    private class WaveTask extends BukkitRunnable {

        private final Vector direction;
        private final Vector perpendicular;
        private int ticksAlive = 0;
        private double distanceTraveled = 0;
        private final Set<String> harvestedBlocks = new HashSet<>();
        private static final double WAVE_SPEED = 0.4;
        private double wavePhase = 0;

        WaveTask(Vector direction, Vector perpendicular) {
            this.direction = direction.clone();
            this.perpendicular = perpendicular.clone();
        }

        @Override
        public void run() {
            ticksAlive++;
            wavePhase += 0.3;

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

            // Nettoyer les axolotls morts
            axolotls.removeIf(a -> a.entity == null || a.entity.isDead() || !a.entity.isValid());

            if (axolotls.isEmpty()) {
                cleanup();
                cancel();
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // FIN DE LA VAGUE
            // ═══════════════════════════════════════════════════════════
            if (distanceTraveled >= waveDistance) {
                cleanup();
                cancel();

                // Retour de vague?
                if (hasReturnWave && !isReturnWave) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (owner.isOnline()) {
                            owner.playSound(owner.getLocation(), Sound.ENTITY_AXOLOTL_SPLASH, 0.8f, 1.2f);
                            startWave(true);
                        } else {
                            finishAnimation();
                        }
                    }, 20L);
                } else {
                    finishAnimation();
                }
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT DE LA VAGUE
            // ═══════════════════════════════════════════════════════════
            distanceTraveled += WAVE_SPEED;

            Location waveCenter = isReturnWave ?
                centerLocation.clone().add(waveDirection.clone().multiply(waveDistance - distanceTraveled)) :
                centerLocation.clone().add(waveDirection.clone().multiply(distanceTraveled));

            // ═══════════════════════════════════════════════════════════
            // PARTICULES DE VAGUE
            // ═══════════════════════════════════════════════════════════
            if (showParticles && ticksAlive % 3 == 0) {
                // Ligne de vague avec mouvement ondulé (un point sur 2)
                for (int i = 0; i < waveWidth; i += 2) {
                    double offset = -waveWidth / 2.0 + i;
                    double waveHeight = Math.sin(wavePhase + i * 0.5) * 0.3 + 0.5;

                    Location waveLoc = waveCenter.clone()
                        .add(perpendicular.clone().multiply(offset))
                        .add(0, waveHeight, 0);

                    // Particules d'eau
                    owner.spawnParticle(Particle.SPLASH, waveLoc, 1, 0.1, 0.1, 0.1, 0.05);

                    // Écume (moins fréquent)
                    if (i % 4 == 0) {
                        Particle.DustOptions foam = new Particle.DustOptions(FOAM_WHITE, 1.0f);
                        owner.spawnParticle(Particle.DUST, waveLoc.clone().add(0, 0.2, 0), 1, 0.1, 0.1, 0.1, 0, foam);
                    }
                }

                // Bulles derrière la vague
                if (ticksAlive % 6 == 0) {
                    Location bubbleLoc = waveCenter.clone()
                        .add(direction.clone().multiply(-1))
                        .add(perpendicular.clone().multiply((random.nextDouble() - 0.5) * waveWidth));
                    owner.spawnParticle(Particle.BUBBLE_POP, bubbleLoc, 2, 0.3, 0.2, 0.3, 0.02);
                }
            }

            // ═══════════════════════════════════════════════════════════
            // METTRE À JOUR CHAQUE AXOLOTL
            // ═══════════════════════════════════════════════════════════
            for (AxolotlInstance axolotl : axolotls) {
                updateAxolotl(axolotl, waveCenter, world);
            }

            // Sons de vague
            if (ticksAlive % 20 == 0) {
                owner.playSound(waveCenter, Sound.ENTITY_AXOLOTL_SWIM, 0.4f, 1.0f + random.nextFloat() * 0.2f);
            }
            if (ticksAlive % 40 == 0) {
                owner.playSound(waveCenter, Sound.BLOCK_WATER_AMBIENT, 0.3f, 1.2f);
            }
        }

        private void updateAxolotl(AxolotlInstance axolotl, Location waveCenter, World world) {
            // Position sur la vague
            double waveHeight = Math.sin(wavePhase + axolotl.offsetFromCenter * 0.3) * 0.2 + 0.3;

            Location targetLoc = waveCenter.clone()
                .add(perpendicular.clone().multiply(axolotl.offsetFromCenter))
                .add(0, waveHeight, 0);

            // Trouver le sol
            targetLoc = findGroundLevel(targetLoc, world);
            if (targetLoc == null) return;
            targetLoc.add(0, waveHeight, 0);

            // Orienter l'axolotl dans la direction de la vague
            targetLoc.setDirection(direction);
            axolotl.entity.teleport(targetLoc);

            // ═══════════════════════════════════════════════════════════
            // VÉRIFIER LE "PLAY DEAD"
            // ═══════════════════════════════════════════════════════════
            if (axolotl.isPlayingDead) {
                axolotl.playDeadTicks++;
                axolotl.entity.setPlayingDead(true);

                // Fin du play dead après 20 ticks
                if (axolotl.playDeadTicks > 20) {
                    axolotl.isPlayingDead = false;
                    axolotl.entity.setPlayingDead(false);
                }
            } else if (random.nextDouble() * 100 < playDeadChance * 0.1) { // Par tick
                // Déclencher un play dead
                axolotl.isPlayingDead = true;
                axolotl.playDeadTicks = 0;
                totalPlayDeads++;

                if (onPlayDead != null) {
                    onPlayDead.accept(totalPlayDeads);
                }

                // Effet visuel
                if (showParticles) {
                    owner.spawnParticle(Particle.HEART, targetLoc.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
                }
                owner.playSound(targetLoc, Sound.ENTITY_AXOLOTL_HURT, 0.5f, 1.5f);
            }

            // ═══════════════════════════════════════════════════════════
            // RÉCOLTER LES CULTURES
            // ═══════════════════════════════════════════════════════════
            int harvestRadius = axolotl.isBlue ? 2 : 1; // Bleu = rayon double!
            int cx = targetLoc.getBlockX();
            int cy = targetLoc.getBlockY();
            int cz = targetLoc.getBlockZ();

            for (int x = -harvestRadius; x <= harvestRadius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -harvestRadius; z <= harvestRadius; z++) {
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

                            // Bonus pour axolotl bleu
                            if (axolotl.isBlue && random.nextBoolean()) {
                                totalBlueBonuses++;
                                if (onBlueBonus != null && totalBlueBonuses % 5 == 0) {
                                    onBlueBonus.accept(totalBlueBonuses);
                                }

                                // Effet spécial bleu
                                if (showParticles) {
                                    owner.spawnParticle(Particle.END_ROD,
                                        blockLoc.clone().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1, 0.02);
                                }
                            }

                            // Effet de récolte
                            if (showParticles) {
                                owner.spawnParticle(Particle.SPLASH,
                                    blockLoc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.05);
                            }
                        }
                    }
                }
            }

            // Particules autour de l'axolotl
            if (showParticles && ticksAlive % 5 == 0) {
                owner.spawnParticle(Particle.SPLASH, targetLoc, 1, 0.2, 0.1, 0.2, 0.02);

                // Particules spéciales pour le bleu
                if (axolotl.isBlue && ticksAlive % 10 == 0) {
                    Particle.DustOptions blueDust = new Particle.DustOptions(WAVE_CYAN, 1.2f);
                    owner.spawnParticle(Particle.DUST, targetLoc.clone().add(0, 0.3, 0), 2, 0.2, 0.2, 0.2, 0, blueDust);
                }
            }
        }

        private void cleanup() {
            for (AxolotlInstance axolotl : axolotls) {
                if (axolotl.entity != null && !axolotl.entity.isDead()) {
                    Location loc = axolotl.entity.getLocation();

                    if (showParticles && owner.isOnline()) {
                        owner.spawnParticle(Particle.SPLASH, loc, 8, 0.3, 0.3, 0.3, 0.1);
                        owner.spawnParticle(Particle.BUBBLE_POP, loc, 5, 0.2, 0.2, 0.2, 0.05);
                    }

                    axolotl.entity.remove();
                }
            }
            axolotls.clear();
        }
    }

    private Location findGroundLevel(Location loc, World world) {
        int startY = loc.getBlockY();
        for (int y = startY; y >= startY - 5; y--) {
            Location checkLoc = new Location(world, loc.getBlockX(), y, loc.getBlockZ());
            Block block = checkLoc.getBlock();
            if (!block.isPassable() && block.getType().isSolid()) {
                return checkLoc.clone().add(0.5, 1, 0.5);
            }
        }
        for (int y = startY + 1; y <= startY + 5; y++) {
            Location checkLoc = new Location(world, loc.getBlockX(), y, loc.getBlockZ());
            Block block = checkLoc.getBlock();
            if (!block.isPassable() && block.getType().isSolid()) {
                return checkLoc.clone().add(0.5, 1, 0.5);
            }
        }
        return loc;
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
            owner.playSound(owner.getLocation(), Sound.ENTITY_AXOLOTL_SPLASH, 0.6f, 1.3f);
            owner.playSound(owner.getLocation(), Sound.AMBIENT_UNDERWATER_EXIT, 0.5f, 1.0f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalBlueBonuses);
        }
    }
}
