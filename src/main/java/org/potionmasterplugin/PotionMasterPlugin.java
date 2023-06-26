package org.potionmasterplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PotionMasterPlugin extends JavaPlugin implements Listener {

    private static final String PERMISSION_USE = "potionmaster.use";
    private FileConfiguration config;
    private Map<String, Integer> activePlayers = new HashMap<>();
    private Map<String, Long> consumingPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("PotionMasterPlugin está ativado e funcionando!");
        getLogger().info("Criado por: Mrgamer200 & MrMadaraUchiha");
        getLogger().info("Nome do plugin: PotionMasterPlugin v1.0");
        getLogger().info("Obrigado por usar o meu plugin!");

        // Carregar a configuração
        config = getConfig();
        config.options().copyDefaults(true);
        saveConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("PotionMasterPlugin").setExecutor(this);
        getCommand("pot").setExecutor(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("PotionMasterPlugin está desativado!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("PotionMasterPlugin")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("potionmaster.reload") || sender.isOp()) {
                    reloadPlugin();
                    sender.sendMessage(ChatColor.GREEN + "O plugin PotionMasterPlugin foi recarregado.");
                    return true;
                } else {
                    sender.sendMessage("Você não tem permissão para usar esse comando.");
                    return false;
                }
            }
        }

        if (cmd.getName().equalsIgnoreCase("pot")) {
            if (args.length >= 1) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (player.hasPermission(PERMISSION_USE) || player.isOp()) {
                        ItemStack potion = new ItemStack(Material.POTION);
                        PotionMeta meta = (PotionMeta) potion.getItemMeta();

                        meta.setDisplayName(ChatColor.RESET + "PotionMaster");

                        List<PotionEffect> effects = new ArrayList<>();

                        for (int i = 0; i < args.length; i++) {
                            String[] effectData = args[i].split(":");
                            if (effectData.length == 3) {
                                PotionEffectType effectType = PotionEffectType.getByName(effectData[0].toUpperCase());
                                if (effectType != null) {
                                    int effectAmplifier = Integer.parseInt(effectData[1]) - 1;
                                    int effectDuration = parseDuration(effectData[2]); // Converter a duração para ticks
                                    effects.add(new PotionEffect(effectType, effectDuration, effectAmplifier));
                                } else {
                                    sender.sendMessage("Efeito de poção inválido: " + effectData[0]);
                                    return false;
                                }
                            } else {
                                sender.sendMessage("Formato de efeito inválido: " + args[i]);
                                return false;
                            }
                        }

                        for (PotionEffect effect : effects) {
                            meta.addCustomEffect(effect, true);
                        }

                        meta.setLore(Arrays.asList(ChatColor.GRAY + "PotionMaster Plugin"));
                        potion.setItemMeta(meta);

                        player.getInventory().addItem(potion);

                        return true;
                    } else {
                        sender.sendMessage("Você não tem permissão para usar esse comando.");
                        return false;
                    }
                } else {
                    sender.sendMessage("Este comando só pode ser executado por jogadores.");
                }
            } else {
                sender.sendMessage("Uso correto: /pot <efeito1:amplificador:duração> [efeito2:amplificador:duração] ...");
            }
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.POTION) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();

            if (meta.hasCustomEffects()) {
                if (event.getAction().toString().contains("RIGHT")) {
                    if (item.getAmount() == 1) { // Verificar se é a última poção na mão do jogador
                        if (!consumingPlayers.containsKey(player.getName())) {
                            consumingPlayers.put(player.getName(), System.currentTimeMillis());
                        } else {
                            long lastConsumptionTime = consumingPlayers.get(player.getName());
                            if (System.currentTimeMillis() - lastConsumptionTime < 1500) {
                                player.sendMessage(ChatColor.RED + "Você ainda está consumindo a poção.");
                                return; // Ainda está consumindo a poção
                            } else {
                                consumingPlayers.put(player.getName(), System.currentTimeMillis());
                            }
                        }

                        event.setCancelled(true); // Cancelar o evento para impedir a ação padrão de beber a poção

                        // Verificar se o jogador já tem efeitos ativos
                        if (!activePlayers.containsKey(player.getName())) {
                            // Aplicar os efeitos de poção
                            for (PotionEffect effect : meta.getCustomEffects()) {
                                player.addPotionEffect(effect);
                            }

                            activePlayers.put(player.getName(), (int) (System.currentTimeMillis() / 1000)); // Armazenar o tempo de ativação dos efeitos

                            // Agendar uma tarefa para remover os efeitos quando a duração acabar
                            int effectDuration = 0;
                            for (PotionEffect effect : meta.getCustomEffects()) {
                                if (effect.getDuration() > effectDuration) {
                                    effectDuration = effect.getDuration();
                                }
                            }
                            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                                @Override
                                public void run() {
                                    if (activePlayers.containsKey(player.getName())) {
                                        for (PotionEffect effect : meta.getCustomEffects()) {
                                            player.removePotionEffect(effect.getType());
                                        }
                                        activePlayers.remove(player.getName());
                                    }
                                }
                            }, effectDuration); // Agendar a remoção após a duração dos efeitos

                            // Remover a poção da mão do jogador
                            item.setAmount(0);
                            player.getInventory().setItemInMainHand(item);
                        } else {
                            player.sendMessage(ChatColor.RED + "Você já possui efeitos de poção ativos.");
                        }
                    }
                }
            }
        }
    }

    private void reloadPlugin() {
        reloadConfig();
        config = getConfig();
    }

    private int parseDuration(String duration) {
        String[] parts = duration.split(":");
        int ticks = 0;
        for (String part : parts) {
            char unit = part.charAt(part.length() - 1);
            int amount = Integer.parseInt(part.substring(0, part.length() - 1));
            switch (unit) {
                case 's':
                    ticks += amount * 20; // Segundos para ticks
                    break;
                case 'm':
                    ticks += amount * 1200; // Minutos para ticks
                    break;
                case 'h':
                    ticks += amount * 72000; // Horas para ticks
                    break;
                case 'd':
                    ticks += amount * 1728000; // Dias para ticks
                    break;
                default:
                    break;
            }
        }
        return ticks;
    }
}
