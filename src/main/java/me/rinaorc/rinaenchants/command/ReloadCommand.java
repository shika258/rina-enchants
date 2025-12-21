package me.rinaorc.rinaenchants.command;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final RinaEnchantsPlugin plugin;

    public ReloadCommand(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("rinaenchants.reload")) {
                    sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'exécuter cette commande!");
                    return true;
                }
                
                try {
                    plugin.reload();
                    sender.sendMessage(ChatColor.GREEN + "✓ RinaEnchants rechargé avec succès!");
                    sender.sendMessage(ChatColor.GRAY + "  Configuration et enchantements mis à jour.");
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "✗ Erreur lors du rechargement: " + e.getMessage());
                    plugin.getLogger().severe("Erreur lors du reload: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
                
            case "info":
                sendInfo(sender);
                break;

            case "cleanup":
                if (!sender.hasPermission("rinaenchants.cleanup")) {
                    sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'exécuter cette commande!");
                    return true;
                }

                int cleaned = plugin.forceCleanupAllEntities();
                if (cleaned > 0) {
                    sender.sendMessage(ChatColor.GREEN + "✓ " + cleaned + " entité(s) d'enchantement supprimée(s)!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Aucune entité d'enchantement à nettoyer.");
                }
                break;

            case "help":
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.YELLOW + "RinaEnchants" + ChatColor.GOLD + " ═══════");
        sender.sendMessage(ChatColor.GRAY + "Plugin d'enchantements pour RivalHarvesterHoes");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/rinaenchants reload" + ChatColor.GRAY + " - Recharge la configuration");
        sender.sendMessage(ChatColor.YELLOW + "/rinaenchants info" + ChatColor.GRAY + " - Affiche les infos du plugin");
        sender.sendMessage(ChatColor.YELLOW + "/rinaenchants cleanup" + ChatColor.GRAY + " - Supprime les entités orphelines");
        sender.sendMessage(ChatColor.YELLOW + "/rinaenchants help" + ChatColor.GRAY + " - Affiche cette aide");
        sender.sendMessage(ChatColor.GOLD + "══════════════════════════");
        sender.sendMessage("");
    }
    
    private void sendInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "═══════ " + ChatColor.YELLOW + "RinaEnchants Info" + ChatColor.GOLD + " ═══════");
        sender.sendMessage(ChatColor.GRAY + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Auteur: " + ChatColor.WHITE + "Rinaorc Studio");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Enchantements disponibles:");
        
        // Bee Collector
        boolean beeEnabled = plugin.getConfig().getBoolean("bee-collector.enabled", true);
        sender.sendMessage(ChatColor.GRAY + " • " + (beeEnabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗") + 
                          ChatColor.YELLOW + " Bee Collector " + ChatColor.GRAY + "(Apiculteur)");
        
        // Panda Roll
        boolean pandaEnabled = plugin.getConfig().getBoolean("panda-roll.enabled", true);
        sender.sendMessage(ChatColor.GRAY + " • " + (pandaEnabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗") + 
                          ChatColor.YELLOW + " Panda Roll " + ChatColor.GRAY + "(Roulade de Panda)");
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "API RivalHarvesterHoes: " + 
                          (plugin.getHoesAPI() != null ? ChatColor.GREEN + "Connectée" : ChatColor.RED + "Non connectée"));
        sender.sendMessage(ChatColor.GOLD + "══════════════════════════════");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "info", "cleanup", "help");
            String input = args[0].toLowerCase();
            
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }
        
        return completions;
    }
}
