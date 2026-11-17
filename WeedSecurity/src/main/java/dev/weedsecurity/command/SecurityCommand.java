package dev.weedsecurity.command;

import dev.weedlib.text.Text;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import dev.weedsecurity.menu.SecurityMenus;
import dev.weedsecurity.security.PinService;
import dev.weedsecurity.security.LuckPermsBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class SecurityCommand implements TabExecutor {
 private static final String PREFIX=Text.color("&8[&dWEEDSECURITY&8] &7");
 private final WeedSecurity plugin;private final SecurityDatabase db;private final PinService pins;private final SecurityMenus menus;
 public SecurityCommand(WeedSecurity plugin,SecurityDatabase db,PinService pins,SecurityMenus menus){this.plugin=plugin;this.db=db;this.pins=pins;this.menus=menus;}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(sender instanceof Player p&&!p.isOp()){sender.sendMessage(PREFIX+"§cВ доступе отказано.");return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("panel")){if(!(sender instanceof Player p)){sender.sendMessage(PREFIX+"§cТолько для игрока.");return true;}menus.openPlugins(p);return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("playerlist")){if(!(sender instanceof Player p)){sender.sendMessage(PREFIX+"§cТолько для игрока.");return true;}menus.openPlayers(p);return true;}
  if(args.length==2&&args[0].equalsIgnoreCase("repass")){OfflinePlayer target=Bukkit.getOfflinePlayer(args[1]);if(!target.hasPlayedBefore()&&!target.isOnline()){sender.sendMessage(PREFIX+"§cИгрок не найден.");return true;}pins.reset(target.getUniqueId());db.audit(sender instanceof Player p?p.getUniqueId():null,target.getUniqueId(),"PIN_RESET_REQUESTED","Administrator requested PIN reset");if(target.isOnline())target.getPlayer().sendMessage(PREFIX+"§eАдминистратор сбросил ваш PIN. Установите новый: §f/pincode set вашпинкод");sender.sendMessage(PREFIX+"§aПовторная установка PIN запрошена для §f"+args[1]);return true;}
  if(args.length==2&&args[0].equalsIgnoreCase("giveop")){giveOp(sender,args[1]);return true;}
  if(args.length==1&&args[0].equalsIgnoreCase("info")){sender.sendMessage("§8§m────── §dWeedSecurity §8§m──────");sender.sendMessage("§7Storage: §fSQLite");sender.sendMessage("§7Modules: §fPIN · Audit · GUI · Operator Allowlist");sender.sendMessage("§8§m────────────────────────");return true;}
  if(args.length==4&&args[0].equalsIgnoreCase("permission")){permission(sender,args);return true;}
  sender.sendMessage(PREFIX+"§f/weedsecurity panel");sender.sendMessage(PREFIX+"§f/weedsecurity playerlist");sender.sendMessage(PREFIX+"§f/weedsecurity repass <ник>");sender.sendMessage(PREFIX+"§f/weedsecurity giveop <ник>");sender.sendMessage(PREFIX+"§f/weedsecurity permission <add|remove> <игрок> <группа>");return true;
 }
 private void giveOp(CommandSender sender,String name){
  if(!name.matches("[A-Za-z0-9_]{3,16}")){sender.sendMessage(PREFIX+"§cНекорректный ник игрока.");return;}
  List<String> allowed=plugin.getConfig().getStringList("security.op-allowlist");boolean permitted=allowed.stream().anyMatch(n->n.equalsIgnoreCase(name));
  if(!permitted){String reason=plugin.getConfig().getString("security.op-ban-message","Попробуй еще раз)");OfflinePlayer banned=Bukkit.getOfflinePlayer(name);banned.ban(reason,(java.time.Instant)null,sender.getName());Player online=Bukkit.getPlayerExact(name);if(online!=null)online.kickPlayer("§c"+reason);db.audit(sender instanceof Player p?p.getUniqueId():null,banned.getUniqueId(),"OP_ALLOWLIST_BAN","Profile is not present in security.op-allowlist");sender.sendMessage(PREFIX+"§cИгрок отсутствует в allowlist. Выдан перманентный бан.");return;}
  OfflinePlayer target=Bukkit.getOfflinePlayer(name);target.setOp(true);db.audit(sender instanceof Player p?p.getUniqueId():null,target.getUniqueId(),"OP_GRANTED","Operator granted through allowlist");sender.sendMessage(PREFIX+"§aОператор выдан §f"+name+"§a.");if(target.isOnline())target.getPlayer().sendMessage(PREFIX+"§aВам выданы права оператора.");
 }
 private void permission(CommandSender sender,String[] args){
  String action=args[1].toLowerCase(Locale.ROOT);
  if(!action.equals("add")&&!action.equals("remove")){sender.sendMessage(PREFIX+"§cИспользуйте add или remove.");return;}
  LuckPermsBridge lp=LuckPermsBridge.find();
  if(lp==null){sender.sendMessage(PREFIX+"§eLuckPerms не установлен или отключён. Остальные функции WeedSecurity работают независимо.");return;}
  UUID target=Bukkit.getOfflinePlayer(args[2]).getUniqueId();
  String groupName=lp.getLoadedGroupNames().stream().filter(name->name.equalsIgnoreCase(args[3])).findFirst().orElse(null);
  if(groupName==null){sender.sendMessage(PREFIX+"§cГруппа не найдена в LuckPerms.");return;}
  sender.sendMessage(PREFIX+"§7Выполняется операция...");
  lp.updateGroup(target,groupName,action.equals("add")).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{
   if(error!=null){sender.sendMessage(PREFIX+"§cНе удалось обновить группу.");plugin.getLogger().warning("LuckPerms update failed: "+error.getMessage());return;}
   db.audit(sender instanceof Player p?p.getUniqueId():null,target,"LP_GROUP_"+action.toUpperCase(Locale.ROOT),"group="+groupName);
   sender.sendMessage(PREFIX+"§aГруппа обновлена: §f"+groupName+" §7→ §f"+args[2]);
  }));
 }

 @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(sender instanceof Player p&&!p.isOp())return List.of();if(args.length==1)return filter(List.of("panel","playerlist","repass","giveop","permission","info"),args[0]);if(args.length==2&&(args[0].equalsIgnoreCase("repass")||args[0].equalsIgnoreCase("giveop"))){List<String> n=new ArrayList<>();Bukkit.getOnlinePlayers().forEach(p->n.add(p.getName()));if(args[0].equalsIgnoreCase("giveop"))n.addAll(plugin.getConfig().getStringList("security.op-allowlist"));return filter(n,args[1]);}if(args.length==2&&args[0].equalsIgnoreCase("permission"))return filter(List.of("add","remove"),args[1]);if(args.length==3&&args[0].equalsIgnoreCase("permission")){List<String> n=new ArrayList<>();Bukkit.getOnlinePlayers().forEach(p->n.add(p.getName()));return filter(n,args[2]);}if(args.length==4&&args[0].equalsIgnoreCase("permission")){LuckPermsBridge lp=LuckPermsBridge.find();if(lp!=null)return filter(lp.getLoadedGroupNames(),args[3]);}return List.of();}
 private List<String> filter(List<String> values,String prefix){return values.stream().filter(v->v.regionMatches(true,0,prefix,0,prefix.length())).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
}
