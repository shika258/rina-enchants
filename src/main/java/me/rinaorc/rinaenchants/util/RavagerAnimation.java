package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation du Ravageur - STAMPEDE / Charge Dévastatrice
 *
 * Mécanique unique en 3 phases:
 * 1. CHARGE - Le Ravageur fonce en ligne droite, écrasant les cultures (3 blocs de large)
 * 2. STOMP - Toutes les X blocs, onde de choc circulaire qui récolte autour
 * 3. ROAR - À la fin, rugissement = explosion de récolte massive
 *
 * Effets visuels brutaux: particules de terre, screen shake, sons puissants
 */
public class RavagerAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final Vector direction;
    private final Player owner;
    private final int chargeDistance;
    private final int stompInterval;
    private final int stompRadius;
    private final int roarRadius;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Set<Material> crops;
    private final Set<Material> noAgeCrops;

    private Consumer<Location> onCropHit;
    private Runnable onStomp;
    private BiConsumer<Integer, Integer> onFinish; // totalCrops, totalStomps

    private Ravager ravagerEntity;
    private int totalCropsHarvested = 0;
    private int totalStomps = 0;
    private final Set<String> harvestedBlocks = new HashSet<>();

    // Paramètres de mouvement
    private static final double CHARGE_SPEED = 0.6; // Blocs par tick (rapide!)
    private static final int PATH_WIDTH = 3; // Largeur du chemin de destruction

    public RavagerAnimation(RinaEnchantsPlugin plugin, Location startLocation, Vector direction,
        Player owner, int chargeDistance, int stompInterval, int stompRadius,
        int roarRadius, boolean showParticles, boolean clientSideOnly,
        Set<Material> crops, Set<Material> noAgeCrops) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.direction = direction.clone().normalize();
        this.owner = owner;
        this.chargeDistance = chargeDistance;
        this.stompInterval = stompInterval;
        this.stompRadius = stompRadius;
        this.roarRadius = roarRadius;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.crops = crops;
        this.noAgeCrops = noAgeCrops;
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnStomp(Runnable callback) {
        this.onStomp = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        World world = startLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // SPAWN DU RAVAGEUR
        // ═══════════════════════════════════════════════════════════

        try {
            ravagerEntity = (Ravager) world.spawnEntity(startLocation, EntityType.RAVAGER);
            ravagerEntity.setInvulnerable(true);
            ravagerEntity.setSilent(true); // On gère nos propres sons
            ravagerEntity.setAI(false);
            ravagerEntity.setGravity(false);
            ravagerEntity.setCollidable(false);
            ravagerEntity.setRemoveWhenFarAway(false);

            // ═══════════════════════════════════════════════════════════
            // MARQUER L'ENTITÉ pour cleanup après reboot
            // ═══════════════════════════════════════════════════════════
            plugin.markAsEnchantEntity(ravagerEntity);

            // Orienter le Ravageur dans la direction de charge
            Location lookAt = startLocation.clone().add(direction);
            ravagerEntity.teleport(startLocation.setDirection(direction));

            // Client-side uniquement
            if (clientSideOnly) {
                plugin.makeEntityClientSide(ravagerEntity, owner);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("§c[RinaEnchants] Impossible de spawn le Ravageur: " + e.getMessage());
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // EFFETS DE SPAWN ÉPIQUES
        // ═══════════════════════════════════════════════════════════

        if (showParticles) {
            // Explosion de particules de terre au spawn
            owner.spawnParticle(Particle.BLOCK, startLocation, 25, 1, 0.5, 1, 0.1,
                Material.DIRT.createBlockData());
            owner.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, startLocation, 10, 0.5, 0.5, 0.5, 0.05);
            owner.spawnParticle(Particle.EXPLOSION, startLocation, 2, 0.5, 0.5, 0.5, 0);
        }

        // Son de spawn brutal
        owner.playSound(startLocation, Sound.ENTITY_RAVAGER_AMBIENT, 1.5f, 0.7f);
        owner.playSound(startLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // Démarrer l'animation de charge
        new RavagerChargeTask().runTaskTimer(plugin, 5L, 1L);
    }

    /**
     * Task principale de la charge du Ravageur
     */
    private class RavagerChargeTask extends BukkitRunnable {

        private double distanceTraveled = 0;
        private int ticksSinceLastStomp = 0;
        private Location currentPos;

        public RavagerChargeTask() {
            this.currentPos = startLocation.clone();
        }

        @Override
        public void run() {
            // Vérifier si le propriétaire est toujours en ligne
            if (!owner.isOnline()) {
                cleanup(false);
                cancel();
                return;
            }

            // Vérifier si le ravageur existe
            if (ravagerEntity == null || ravagerEntity.isDead() || !ravagerEntity.isValid()) {
                cancel();
                if (onFinish != null) {
                    onFinish.accept(totalCropsHarvested, totalStomps);
                }
                return;
            }

            World world = currentPos.getWorld();
            if (world == null) {
                cleanup(false);
                cancel();
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // FIN DE CHARGE - RUGISSEMENT FINAL
            // ═══════════════════════════════════════════════════════════

            if (distanceTraveled >= chargeDistance) {
                performRoar(currentPos, world);
                cleanup(true);
                cancel();
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT DE CHARGE
            // ═══════════════════════════════════════════════════════════

            // Avancer le Ravageur
            Vector movement = direction.clone().multiply(CHARGE_SPEED);
            currentPos.add(movement);
            distanceTraveled += CHARGE_SPEED;
            ticksSinceLastStomp++;

            // Ajuster la hauteur au terrain
            Location groundPos = findGround(currentPos, world);
            groundPos.setDirection(direction);
            ravagerEntity.teleport(groundPos);

            // ═══════════════════════════════════════════════════════════
            // ÉCRASEMENT DES CULTURES SUR LE CHEMIN
            // ═══════════════════════════════════════════════════════════

            harvestPath(groundPos, world);

            // ═══════════════════════════════════════════════════════════
            // EFFETS DE CHARGE
            // ═══════════════════════════════════════════════════════════

            if (showParticles) {
                // Particules de poussière derrière le Ravageur
                Location dustLoc = groundPos.clone().subtract(direction.clone().multiply(1.5));
                owner.spawnParticle(Particle.BLOCK, dustLoc, 4, 0.5, 0.2, 0.5, 0.1,
                    Material.DIRT.createBlockData());
                owner.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dustLoc, 1, 0.3, 0.1, 0.3, 0.02);

                // Particules de rage autour du Ravageur
                if (distanceTraveled % 5 < CHARGE_SPEED) {
                    owner.spawnParticle(Particle.ANGRY_VILLAGER, groundPos.clone().add(0, 1.5, 0),
                        1, 0.5, 0.3, 0.5, 0);
                }
            }

            // Son de pas lourds
            if (distanceTraveled % 2 < CHARGE_SPEED) {
                owner.playSound(groundPos, Sound.ENTITY_RAVAGER_STEP, 0.8f, 0.9f);
            }

            // ═══════════════════════════════════════════════════════════
            // STOMP PÉRIODIQUE - ONDE DE CHOC
            // ═══════════════════════════════════════════════════════════

            if (ticksSinceLastStomp >= stompInterval) {
                performStomp(groundPos, world);
                ticksSinceLastStomp = 0;
            }
        }

        /**
         * Récolte les cultures sur le chemin du Ravageur (largeur PATH_WIDTH)
         */
        private void harvestPath(Location center, World world) {
            // Calculer le vecteur perpendiculaire pour la largeur
            Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

            int halfWidth = PATH_WIDTH / 2;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                for (int y = -1; y <= 1; y++) {
                    Location checkLoc = center.clone()
                        .add(perpendicular.clone().multiply(w))
                        .add(0, y, 0);

                    harvestAt(checkLoc);
                }
            }
        }

        /**
         * STOMP - Onde de choc circulaire (optimisé)
         */
        private void performStomp(Location center, World world) {
            totalStomps++;

            if (showParticles) {
                // Cercle de particules simplifié (moins de particules)
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 3) {
                    double x = Math.cos(angle) * stompRadius;
                    double z = Math.sin(angle) * stompRadius;
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    owner.spawnParticle(Particle.BLOCK, particleLoc, 3, 0.3, 0.1, 0.3, 0,
                        Material.DIRT.createBlockData());
                }

                // Effet de shockwave
                owner.spawnParticle(Particle.EXPLOSION, center, 1, 0.3, 0.1, 0.3, 0);
            }

            // Sons de stomp
            owner.playSound(center, Sound.ENTITY_RAVAGER_STUNNED, 0.8f, 0.6f);

            // Récolter en cercle (avec limite)
            int harvested = 0;
            int maxPerStomp = 20; // Limite pour éviter le lag

            for (int x = -stompRadius; x <= stompRadius && harvested < maxPerStomp; x++) {
                for (int z = -stompRadius; z <= stompRadius && harvested < maxPerStomp; z++) {
                    if (x * x + z * z <= stompRadius * stompRadius) {
                        for (int y = -1; y <= 2; y++) {
                            Location cropLoc = center.clone().add(x, y, z);
                            if (harvestAt(cropLoc)) {
                                harvested++;
                                if (harvested >= maxPerStomp) break;
                            }
                        }
                    }
                }
            }

            // Callback
            if (onStomp != null) {
                onStomp.run();
            }
        }

        /**
         * ROAR - Rugissement final avec explosion de récolte (optimisé)
         */
        private void performRoar(Location center, World world) {
            if (showParticles) {
                // Effet visuel simplifié mais toujours épique
                owner.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
                owner.spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0);

                // Cercle de particules (simplifié)
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = Math.cos(angle) * roarRadius;
                    double z = Math.sin(angle) * roarRadius;
                    Location particleLoc = center.clone().add(x, 0.5, z);
                    owner.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 2, 0.2, 0.2, 0.2, 0);
                }

                // Quelques particules de rage
                for (int i = 0; i < 5; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double dist = Math.random() * roarRadius;
                    Location rageLoc = center.clone().add(
                        Math.cos(angle) * dist,
                        0.5 + Math.random(),
                        Math.sin(angle) * dist
                    );
                    owner.spawnParticle(Particle.ANGRY_VILLAGER, rageLoc, 1, 0, 0, 0, 0);
                }
            }

            // Sons du rugissement
            owner.playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
            owner.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);

            // Récolte en cercle (avec limite pour éviter le lag)
            int harvested = 0;
            int maxPerRoar = 50; // Limite pour éviter le lag

            for (int x = -roarRadius; x <= roarRadius && harvested < maxPerRoar; x++) {
                for (int z = -roarRadius; z <= roarRadius && harvested < maxPerRoar; z++) {
                    if (x * x + z * z <= roarRadius * roarRadius) {
                        for (int y = -1; y <= 2; y++) {
                            Location cropLoc = center.clone().add(x, y, z);
                            if (harvestAt(cropLoc)) {
                                harvested++;
                                if (harvested >= maxPerRoar) break;
                            }
                        }
                    }
                }
            }
        }

        /**
         * Tente de récolter une culture à une position
         * @return true si une culture a été récoltée
         */
        private boolean harvestAt(Location loc) {
            String key = loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
            if (harvestedBlocks.contains(key)) return false;

            Block block = loc.getBlock();
            if (!isMatureCrop(block)) return false;

            harvestedBlocks.add(key);
            totalCropsHarvested++;

            if (onCropHit != null) {
                onCropHit.accept(loc);
            }
            return true;
        }

        /**
         * Trouve le sol sous une position
         */
        private Location findGround(Location loc, World world) {
            Location ground = loc.clone();

            // Chercher vers le bas
            for (int i = 0; i < 5; i++) {
                Block below = ground.clone().subtract(0, 1, 0).getBlock();
                if (!below.getType().isAir() && below.getType().isSolid()) {
                    return ground;
                }
                ground.subtract(0, 1, 0);
            }

            // Chercher vers le haut si on est dans le sol
            ground = loc.clone();
            for (int i = 0; i < 5; i++) {
                Block at = ground.getBlock();
                if (at.getType().isAir() || !at.getType().isSolid()) {
                    return ground;
                }
                ground.add(0, 1, 0);
            }

            return loc;
        }

        /**
         * Vérifie si un bloc est une culture mature
         */
        private boolean isMatureCrop(Block block) {
            Material type = block.getType();

            if (!crops.contains(type)) return false;
            if (noAgeCrops.contains(type)) return true;

            if (block.getBlockData() instanceof Ageable ageable) {
                return ageable.getAge() >= ageable.getMaximumAge();
            }

            return true;
        }

        private void cleanup(boolean withEffects) {
            if (ravagerEntity != null && !ravagerEntity.isDead()) {
                Location loc = ravagerEntity.getLocation();

                if (withEffects && showParticles && owner.isOnline()) {
                    // Effet de disparition épique
                    owner.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 15, 1, 1, 1, 0.1);
                    owner.spawnParticle(Particle.BLOCK, loc, 20, 1, 0.5, 1, 0.1,
                        Material.DIRT.createBlockData());
                    owner.spawnParticle(Particle.LARGE_SMOKE, loc, 8, 0.5, 0.5, 0.5, 0.05);
                }

                if (owner.isOnline()) {
                    owner.playSound(loc, Sound.ENTITY_RAVAGER_HURT, 1.0f, 0.5f);
                }

                ravagerEntity.remove();
            }

            if (onFinish != null) {
                onFinish.accept(totalCropsHarvested, totalStomps);
            }
        }
    }
}
