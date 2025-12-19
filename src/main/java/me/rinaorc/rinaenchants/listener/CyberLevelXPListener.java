package me.rinaorc.rinaenchants.listener;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rivaldev.harvesterhoes.api.events.HoeXPGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour le système de multiplicateur d'XP CyberLevel
 *
 * Fonctionnement:
 * 1. Quand un enchantement proc, on enregistre le multiplicateur pour le joueur
 * 2. Quand HoeXPGainEvent est déclenché, on applique le multiplicateur
 * 3. Le multiplicateur expire après 500ms (pour éviter les fuites mémoire)
 */
public class CyberLevelXPListener implements Listener {

    private final RinaEnchantsPlugin plugin;

    // XP de base par type de culture (chargé depuis la config)
    private final Map<Material, Double> cropXpValues = new HashMap<>();

    // Multiplicateurs actifs par joueur
    // Format: UUID -> {enchantId, multiplier, timestamp, location}
    private final ConcurrentHashMap<UUID, XPMultiplierData> activeMultipliers = new ConcurrentHashMap<>();

    // Durée de validité d'un multiplicateur (en ms)
    private static final long MULTIPLIER_EXPIRY_MS = 2000;

    public CyberLevelXPListener(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        loadCropXpValues();

        // Task de nettoyage des multiplicateurs expirés (toutes les 2 secondes)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredMultipliers, 40L, 40L);

        plugin.getLogger().info("§a[CyberLevel] Listener XP initialisé avec " + cropXpValues.size() + " cultures configurées");
    }

    /**
     * Charge les valeurs d'XP par culture depuis la config
     */
    public void loadCropXpValues() {
        cropXpValues.clear();

        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            plugin.getLogger().info("§e[CyberLevel] Hook désactivé dans la config");
            return;
        }

        List<String> cropsList = plugin.getConfig().getStringList("cyberlevels-hook.xp-settings.specific.crops");

        for (String entry : cropsList) {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    Material material = Material.valueOf(parts[0].trim().toUpperCase());
                    double xp = Double.parseDouble(parts[1].trim());
                    cropXpValues.put(material, xp);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("§e[CyberLevel] Entrée invalide dans la config: " + entry);
            }
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] " + cropXpValues.size() + " valeurs d'XP chargées");
        }
    }

    /**
     * Enregistre un multiplicateur pour un joueur (appelé par les enchantements)
     */
    public void registerMultiplier(UUID playerId, String enchantId, double multiplier, Location location) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        XPMultiplierData data = new XPMultiplierData(enchantId, multiplier, System.currentTimeMillis(), location);
        activeMultipliers.put(playerId, data);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] Multiplicateur x" + multiplier + " enregistré pour " + enchantId);
        }
    }

    /**
     * Vérifie si un multiplicateur est actif pour un joueur
     */
    public boolean hasActiveMultiplier(UUID playerId) {
        XPMultiplierData data = activeMultipliers.get(playerId);
        if (data == null) return false;

        // Vérifier l'expiration
        if (System.currentTimeMillis() - data.timestamp > MULTIPLIER_EXPIRY_MS) {
            activeMultipliers.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * Récupère le multiplicateur actif pour un joueur
     */
    public double getActiveMultiplier(UUID playerId) {
        XPMultiplierData data = activeMultipliers.get(playerId);
        if (data == null) return 1.0;

        // Vérifier l'expiration
        if (System.currentTimeMillis() - data.timestamp > MULTIPLIER_EXPIRY_MS) {
            activeMultipliers.remove(playerId);
            return 1.0;
        }

        return data.multiplier;
    }

    /**
     * Récupère l'XP de base pour un type de culture
     */
    public double getCropBaseXP(Material material) {
        return cropXpValues.getOrDefault(material, 0.0);
    }

    /**
     * Donne l'XP CyberLevel directement au joueur pour une culture cassée.
     * Cette méthode est utilisée quand HellRainAbility.replaceWithDrops ne déclenche pas HoeXPGainEvent.
     *
     * @param player Le joueur qui reçoit l'XP
     * @param cropType Le type de culture cassée
     * @param multiplier Le multiplicateur de l'enchantement (1.0 = pas de multiplicateur)
     */
    public void giveDirectXP(Player player, Material cropType, double multiplier) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        double baseXP = getCropBaseXP(cropType);
        if (baseXP <= 0) {
            return;
        }

        double finalXP = baseXP * multiplier;
        int xpToGive = (int) Math.round(finalXP);

        if (xpToGive <= 0) {
            return;
        }

        // Exécuter la commande CyberLevel pour donner l'XP
        String command = "cyberlevel giveExp " + xpToGive + " " + player.getName();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP donné: " + xpToGive + " à " + player.getName() +
                " (base: " + baseXP + ", multi: x" + multiplier + ", crop: " + cropType + ")");
        }
    }

    /**
     * Écoute l'événement HoeXPGainEvent pour modifier l'XP
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHoeXPGain(HoeXPGainEvent event) {
        if (!plugin.getConfig().getBoolean("cyberlevels-hook.enabled", false)) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();

        // Vérifier si un multiplicateur est actif
        if (!hasActiveMultiplier(playerId)) {
            return;
        }

        double multiplier = getActiveMultiplier(playerId);
        if (multiplier <= 1.0) {
            return;
        }

        // Appliquer le multiplicateur
        double originalXP = event.getXP();
        double newXP = originalXP * multiplier;

        event.setXP(newXP);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("§a[CyberLevel] XP modifié: " + originalXP + " -> " + newXP + " (x" + multiplier + ")");
        }

        // NOTE: On ne consomme PAS le multiplicateur ici car les enchantements
        // récoltent plusieurs cultures. Le multiplicateur expire naturellement après 2s.
    }

    /**
     * Nettoie les multiplicateurs expirés
     */
    private void cleanupExpiredMultipliers() {
        long now = System.currentTimeMillis();
        activeMultipliers.entrySet().removeIf(entry ->
            now - entry.getValue().timestamp > MULTIPLIER_EXPIRY_MS
        );
    }

    /**
     * Réinitialise le listener (appelé lors du reload)
     */
    public void reload() {
        loadCropXpValues();
        activeMultipliers.clear();
    }

    /**
     * Données d'un multiplicateur actif
     */
    private static class XPMultiplierData {
        final String enchantId;
        final double multiplier;
        final long timestamp;
        final Location location;

        XPMultiplierData(String enchantId, double multiplier, long timestamp, Location location) {
            this.enchantId = enchantId;
            this.multiplier = multiplier;
            this.timestamp = timestamp;
            this.location = location;
        }
    }
}
