package dev.weedsecurity.security;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.entity.Player;
import java.util.Locale;

public final class SecurityListener implements Listener {
 private static final String PREFIX="§8[§dWEEDSECURITY§8] §7";
 private final WeedSecurity plugin; private final PinService pins; private final SecurityDatabase db;
 public SecurityListener(WeedSecurity plugin,PinService pins,SecurityDatabase db){this.plugin=plugin;this.pins=pins;this.db=db;}
 private boolean verified(Player p){if(!plugin.getConfig().getBoolean("security.pin-enabled",true))return true;if(!pins.hasPin(p.getUniqueId()))return false;String ip=p.getAddress()==null?"":p.getAddress().getAddress().getHostAddress();return pins.trusted(p.getUniqueId(),ip)||p.hasPermission("weedsecurity.pin.bypass");}
 @EventHandler public void join(PlayerJoinEvent e){if(!plugin.getConfig().getBoolean("security.pin-enabled",true))return;Player p=e.getPlayer();String ip=p.getAddress()==null?"":p.getAddress().getAddress().getHostAddress();if(!pins.hasPin(p.getUniqueId()))p.sendMessage(PREFIX+"§eУ вас еще нет пинкода! Введите его §f> /pincode set вашпинкод");else if(!pins.trusted(p.getUniqueId(),ip))p.sendMessage(PREFIX+"§eВведите PIN командой §f/pincode вашпинкод");db.activity(p.getUniqueId(),"JOIN","Вход на сервер");}
 @EventHandler public void blockBreak(BlockBreakEvent e){if(verified(e.getPlayer()))db.activity(e.getPlayer().getUniqueId(),"BREAK",e.getBlock().getType().name());}
 @EventHandler public void blockPlace(BlockPlaceEvent e){if(verified(e.getPlayer()))db.activity(e.getPlayer().getUniqueId(),"PLACE",e.getBlockPlaced().getType().name());}
 @EventHandler public void serverCommand(ServerCommandEvent e){String root=e.getCommand().trim().toLowerCase(Locale.ROOT).split("\\s+",2)[0].replaceFirst("^/", "");if(root.equals("op")||root.equals("minecraft:op")||root.equals("bukkit:op")){e.setCancelled(true);e.getSender().sendMessage(PREFIX+"§cКоманда op отключена. Используйте /weedsecurity giveop <ник>.");}}
 @EventHandler public void quit(PlayerQuitEvent e){db.activity(e.getPlayer().getUniqueId(),"QUIT","Выход с сервера");}
 @EventHandler public void move(PlayerMoveEvent e){if(!verified(e.getPlayer())&&e.getTo()!=null&&(e.getFrom().getBlockX()!=e.getTo().getBlockX()||e.getFrom().getBlockY()!=e.getTo().getBlockY()||e.getFrom().getBlockZ()!=e.getTo().getBlockZ()))e.setTo(e.getFrom());}
 @EventHandler public void interact(PlayerInteractEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void interactEntity(PlayerInteractEntityEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void chat(AsyncPlayerChatEvent e){if(!verified(e.getPlayer())){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cСначала подтвердите PIN.");}}
 @EventHandler public void command(PlayerCommandPreprocessEvent e){
  String raw=e.getMessage().trim();String lower=raw.toLowerCase(Locale.ROOT);String root=lower.split("\\s+",2)[0];
  if(root.equals("/op")||root.equals("/minecraft:op")||root.equals("/bukkit:op")){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cКоманда /op отключена. Используйте контролируемую выдачу через WeedSecurity.");return;}
  if(root.equals("/weedsecurity")||root.equals("/ws")){if(!e.getPlayer().isOp()){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cВ доступе отказано.");return;}}
  if(!verified(e.getPlayer())&&!(root.equals("/pincode"))){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cСначала подтвердите PIN.");return;}
  if(verified(e.getPlayer())&&!root.equals("/pincode")){String commandName=root.length()>1?root.substring(1):root;String safe="/"+commandName+" (аргументы скрыты)";plugin.getServer().getScheduler().runTask(plugin,()->db.activity(e.getPlayer().getUniqueId(),"COMMAND",safe));}
 }
 @EventHandler public void inventory(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p&&!verified(p))e.setCancelled(true);}
 @EventHandler public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&!verified(p))e.setCancelled(true);}
 @EventHandler public void drop(PlayerDropItemEvent e){if(!verified(e.getPlayer()))e.setCancelled(true);}
 @EventHandler public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&!verified(p))e.setCancelled(true);}
}
