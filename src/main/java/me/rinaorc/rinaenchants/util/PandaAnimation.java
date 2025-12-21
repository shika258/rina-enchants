package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation de Panda avec vraie roulade (setRolling)
 * 
 * Optimisé pour serveurs 500+ joueurs:
 * - Client-side: Visible uniquement par le joueur qui proc
 * - Utilise setRolling(true) pour l'animation native de roulade
 * - Système de combo avec changement de direction
 */
public class PandaAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final Vector direction;
    private final int rollDistance;
    private final double comboChance;
    private final boolean showParticles;
    private final Player owner;
    private final boolean clientSideOnly;
    private final Random random;
    
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onCombo;
    private BiConsumer<Integer, Integer> onFinish;
    
    private Panda pandaEntity;
    private int totalCropsHarvested = 0;
    private int totalCombos = 0;
    
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

    private static final double ROLL_SPEED = 0.5;

    public PandaAnimation(RinaEnchantsPlugin plugin, Location startLocation, Vector direction, 
                         int rollDistance, double comboChance, boolean showParticles,
                         Player owner, boolean clientSideOnly) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.direction = direction.clone().normalize();
        this.rollDistance = rollDistance;
        this.comboChance = comboChance;
        this.showParticles = showParticles;
        this.owner = owner;
        this.clientSideOnly = clientSideOnly;
        this.random = new Random();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }
    
    public void setOnCombo(Consumer<Integer> callback) {
        this.onCombo = callback;
    }
    
    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        startRoll(startLocation, direction);
    }
    
    private void startRoll(Location startPos, Vector rollDirection) {
        World world = startPos.getWorld();
        if (world == null) return;

        try {
            pandaEntity = (Panda) world.spawnEntity(startPos, EntityType.PANDA);
            pandaEntity.setInvulnerable(true);
            pandaEntity.setSilent(false);
            pandaEntity.setAI(false);
            pandaEntity.setGravity(false);
            pandaEntity.setCollidable(false);
            pandaEntity.setCanPickupItems(false);
            pandaEntity.setRemoveWhenFarAway(false);

            // Type de panda aléatoire
            Panda.Gene[] genes = Panda.Gene.values();
            pandaEntity.setMainGene(genes[random.nextInt(genes.length)]);
            pandaEntity.setHiddenGene(genes[random.nextInt(genes.length)]);

            // ═══════════════════════════════════════════════════════════
            // MARQUER L'ENTITÉ pour cleanup après reboot
            // ═══════════════════════════════════════════════════════════
            plugin.markAsEnchantEntity(pandaEntity);

            // ═══════════════════════════════════════════════════════════
            // ANIMATION DE ROULADE NATIVE!
            // ═══════════════════════════════════════════════════════════
            pandaEntity.setRolling(true);

            // Orienter le panda
            Location oriented = startPos.clone();
            oriented.setDirection(rollDirection);
            pandaEntity.teleport(oriented);

            // Client-side: cacher aux autres joueurs
            if (clientSideOnly) {
                plugin.makeEntityClientSide(pandaEntity, owner);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn le panda: " + e.getMessage());
            return;
        }

        if (showParticles) {
            owner.spawnParticle(Particle.CLOUD, startPos, 10, 0.5, 0.3, 0.5, 0.1);
            owner.spawnParticle(Particle.HAPPY_VILLAGER, startPos, 5, 0.3, 0.3, 0.3, 0);
        }
        
        owner.playSound(startPos, Sound.ENTITY_PANDA_SNEEZE, 1.0f, 0.8f);

        new PandaRollTask(startPos, rollDirection).runTaskTimer(plugin, 0L, 1L);
    }

    private class PandaRollTask extends BukkitRunnable {
        
        private final Location currentLocation;
        private final Vector rollDirection;
        private int ticksElapsed = 0;
        private double distanceRolled = 0;
        private final int maxTicks;
        private final Set<String> harvestedBlocks = new HashSet<>();

        public PandaRollTask(Location start, Vector direction) {
            this.currentLocation = start.clone();
            this.rollDirection = direction.clone();
            this.maxTicks = (int)(rollDistance / ROLL_SPEED) + 20;
        }

        @Override
        public void run() {
            ticksElapsed++;

            if (ticksElapsed > maxTicks || distanceRolled >= rollDistance) {
                endRoll();
                return;
            }
            
            if (!owner.isOnline()) {
                cleanup();
                cancel();
                finishAnimation();
                return;
            }

            if (pandaEntity == null || pandaEntity.isDead() || !pandaEntity.isValid()) {
                cancel();
                finishAnimation();
                return;
            }

            World world = currentLocation.getWorld();
            if (world == null) {
                cancel();
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT DE ROULADE
            // ═══════════════════════════════════════════════════════════
            
            Vector movement = rollDirection.clone().multiply(ROLL_SPEED);
            currentLocation.add(movement);
            distanceRolled += ROLL_SPEED;
            
            // Maintenir l'animation de roulade
            pandaEntity.setRolling(true);
            
            // Téléporter le panda
            Location pandaLoc = currentLocation.clone();
            pandaLoc.setDirection(rollDirection);
            pandaEntity.teleport(pandaLoc);
            
            // ═══════════════════════════════════════════════════════════
            // RÉCOLTE DES CULTURES SUR LE PASSAGE
            // ═══════════════════════════════════════════════════════════
            
            int px = currentLocation.getBlockX();
            int py = currentLocation.getBlockY();
            int pz = currentLocation.getBlockZ();
            
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Location blockLoc = new Location(world, px + dx, py + dy, pz + dz);
                        String key = blockLoc.getBlockX() + ":" + blockLoc.getBlockY() + ":" + blockLoc.getBlockZ();
                        
                        if (harvestedBlocks.contains(key)) continue;
                        
                        Block block = blockLoc.getBlock();
                        if (isMatureCrop(block)) {
                            harvestedBlocks.add(key);
                            totalCropsHarvested++;
                            
                            if (onCropHit != null) {
                                onCropHit.accept(blockLoc);
                            }
                            
                            if (showParticles) {
                                owner.spawnParticle(Particle.BLOCK, blockLoc.clone().add(0.5, 0.5, 0.5),
                                    5, 0.3, 0.3, 0.3, 0, block.getBlockData());
                            }
                        }
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════
            // EFFETS VISUELS (uniquement pour le propriétaire)
            // ═══════════════════════════════════════════════════════════
            
            if (showParticles) {
                if (ticksElapsed % 3 == 0) {
                    owner.spawnParticle(Particle.BLOCK, currentLocation.clone().add(0, 0.2, 0),
                        2, 0.3, 0.1, 0.3, 0.1, Material.DIRT.createBlockData());
                }

                owner.spawnParticle(Particle.CLOUD, currentLocation, 1, 0.2, 0.1, 0.2, 0.02);

                if (ticksElapsed % 8 == 0) {
                    owner.spawnParticle(Particle.HAPPY_VILLAGER, currentLocation.clone().add(0, 0.5, 0),
                        2, 0.2, 0.2, 0.2, 0);
                }
            }
            
            if (ticksElapsed % 4 == 0) {
                owner.playSound(currentLocation, Sound.BLOCK_GRASS_STEP, 0.5f, 0.8f);
            }
        }
        
        private void endRoll() {
            cancel();
            
            World world = currentLocation.getWorld();
            if (world == null) {
                finishAnimation();
                return;
            }
            
            // Arrêter la roulade
            if (pandaEntity != null && !pandaEntity.isDead()) {
                pandaEntity.setRolling(false);
            }
            
            if (showParticles) {
                owner.spawnParticle(Particle.CLOUD, currentLocation, 8, 0.5, 0.3, 0.5, 0.1);
            }
            
            // ═══════════════════════════════════════════════════════════
            // VÉRIFICATION DU COMBO
            // ═══════════════════════════════════════════════════════════
            
            double roll = random.nextDouble() * 100;
            
            if (roll < comboChance) {
                totalCombos++;
                
                if (onCombo != null) {
                    onCombo.accept(totalCombos);
                }
                
                if (showParticles) {
                    owner.spawnParticle(Particle.TOTEM_OF_UNDYING, currentLocation.clone().add(0, 1, 0),
                        15, 0.5, 0.5, 0.5, 0.3);
                }
                
                owner.playSound(currentLocation, Sound.ENTITY_PANDA_EAT, 1.0f, 1.5f);
                owner.playSound(currentLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                
                // Nouvelle direction (90°)
                Vector newDirection = getPerpendicularDirection(rollDirection);
                if (random.nextBoolean()) {
                    newDirection.multiply(-1);
                }
                
                // Relancer la roulade après une courte pause
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pandaEntity != null && !pandaEntity.isDead() && owner.isOnline()) {
                        Location newStart = currentLocation.clone();
                        newStart.setDirection(newDirection);
                        pandaEntity.teleport(newStart);
                        pandaEntity.setRolling(true);
                        
                        new PandaRollTask(newStart, newDirection).runTaskTimer(plugin, 5L, 1L);
                    } else {
                        finishAnimation();
                    }
                }, 10L);
                
            } else {
                finishAnimation();
            }
        }
        
        private Vector getPerpendicularDirection(Vector dir) {
            if (Math.abs(dir.getX()) > 0.5) {
                return new Vector(0, 0, 1);
            } else {
                return new Vector(1, 0, 0);
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
    
    private void cleanup() {
        if (pandaEntity != null && !pandaEntity.isDead()) {
            pandaEntity.remove();
        }
    }
    
    private void finishAnimation() {
        if (pandaEntity != null && !pandaEntity.isDead()) {
            Location loc = pandaEntity.getLocation();
            
            if (showParticles && owner.isOnline()) {
                owner.spawnParticle(Particle.POOF, loc, 10, 0.5, 0.5, 0.5, 0.1);
                owner.spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            }
            
            if (owner.isOnline()) {
                owner.playSound(loc, Sound.ENTITY_PANDA_AMBIENT, 1.0f, 1.2f);
            }
            
            pandaEntity.remove();
        }
        
        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalCombos);
        }
    }
}
