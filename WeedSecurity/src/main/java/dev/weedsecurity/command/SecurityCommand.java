package dev.weedsecurity.command;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SecurityCommand implements TabExecutor {
    private static final String PREFIX = "§8[§dWEEDSECURITY§8] §7";
    private final WeedSecurity plugin;
    private final SecurityDatabase db;
    public SecurityCommand(WeedSecurity plugin, SecurityDatabase db) { this.plugin=plugin; this.db=db; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player p) || !p.isOp()) {
            sender.sendMessage(PREFIX + "§cВ доступе отказано."); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            p.sendMessage("§8§m────── §dWeedSecurity §8§m──────");
            p.sendMessage("§7Storage: §fSQLite");
            p.sendMessage("§7Modules: §fPIN · Audit · LuckPerms");
            p.sendMessage("§8§m────────────────────────"); return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("permission")) {
            String action=args[1].toLowerCase(Locale.ROOT);
            if (!action.equals("add") && !action.equals("remove")) { p.sendMessage(PREFIX+"§cИспользуйте add или remove."); return true; }
            String playerName=args[2], groupName=args[3];
            LuckPerms luckPerms=Bukkit.getServicesManager().load(LuckPerms.class);
            if (luckPerms==null) { p.sendMessage(PREFIX+"§cLuckPerms не найден или API недоступен."); return true; }
            UUID targetId=Bukkit.getOfflinePlayer(playerName).getUniqueId();
            Group group=luckPerms.getGroupManager().getGroup(groupName);
            if (group==null) { p.sendMessage(PREFIX+"§cГруппа LuckPerms не найдена: §f"+groupName); return true; }
            p.sendMessage(PREFIX+"§7Выполняется операция...");
            CompletableFuture<User> load=luckPerms.getUserManager().loadUser(targetId);
            load.thenCompose(user -> {
                Node node=Node.builder("group."+group.getName()).build();
                if(action.equals("add")) user.data().add(node); else user.data().remove(node);
                return luckPerms.getUserManager().saveUser(user).thenApply(v -> user);
            }).whenComplete((user,error) -> Bukkit.getScheduler().runTask(plugin,() -> {
                if(!p.isOnline()) return;
                if(error!=null) { p.sendMessage(PREFIX+"§cНе удалось обновить группу. Подробности в консоли."); plugin.getLogger().warning("LuckPerms update failed: "+error.getMessage()); return; }
                db.audit(p.getUniqueId(),targetId,"LP_GROUP_"+action.toUpperCase(Locale.ROOT),"group="+group.getName());
                p.sendMessage(PREFIX+(action.equals("add")?"§aГруппа добавлена: §f":"§eГруппа снята: §f")+group.getName()+" §7→ §f"+playerName);
            }));
            return true;
        }
        p.sendMessage(PREFIX+"§f/weedsecurity permission add <игрок> <группа>");
        p.sendMessage(PREFIX+"§f/weedsecurity permission remove <игрок> <группа>");
        p.sendMessage(PREFIX+"§f/weedsecurity info"); return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player p) || !p.isOp()) return Collections.emptyList();
        LuckPerms lp=Bukkit.getServicesManager().load(LuckPerms.class);
        if(args.length==1) return filter(List.of("permission","info"),args[0]);
        if(args.length==2 && args[0].equalsIgnoreCase("permission")) return filter(List.of("add","remove"),args[1]);
        if(args.length==3 && args[0].equalsIgnoreCase("permission")) {
            List<String> names=new ArrayList<>();
            for(org.bukkit.entity.Player online:Bukkit.getOnlinePlayers()) names.add(online.getName());
            return filter(names,args[2]);
        }
        if(args.length==4 && args[0].equalsIgnoreCase("permission") && lp!=null) {
            List<String> groups=new ArrayList<>();
            lp.getGroupManager().getLoadedGroups().forEach(g -> groups.add(g.getName()));
            return filter(groups,args[3]);
        }
        return Collections.emptyList();
    }
    private List<String> filter(List<String> values,String prefix) {
        return values.stream().filter(v -> v.regionMatches(true,0,prefix,0,prefix.length())).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
