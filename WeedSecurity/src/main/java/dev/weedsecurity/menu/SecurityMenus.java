package dev.weedsecurity.menu;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import dev.weedsecurity.security.PinService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;

/** Administrative inventories. Sensitive values are never read from the PIN database. */
public final class SecurityMenus implements Listener {
    private final WeedSecurity plugin; private final SecurityDatabase db; private final PinService pins;
    public SecurityMenus(WeedSecurity plugin, SecurityDatabase db, PinService pins){this.plugin=plugin;this.db=db;this.pins=pins;}
    private enum View { PLUGINS, PLAYERS, ACTIVITY }
    private static final class Holder implements InventoryHolder {
        final View view; final UUID target; final int page; Inventory inventory;
        Holder(View view, UUID target, int page){this.view=view;this.target=target;this.page=page;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public void openPlugins(Player viewer){openPlugins(viewer,0);}
    private void openPlugins(Player viewer,int page){
        List<Plugin> plugins=Arrays.stream(Bukkit.getPluginManager().getPlugins()).sorted(Comparator.comparing(p->p.getName().toLowerCase(Locale.ROOT))).toList();
        Inventory inv=create(View.PLUGINS,null,page,color(plugin.getConfig().getString("panel.title","§8WeedSecurity §7| §dПлагины")));
        int slot=0; for(Plugin p:plugins.stream().skip((long)page*45).limit(45).toList()){if(slot>=45)break;boolean enabled=p.isEnabled();ItemStack item=new ItemStack(enabled?Material.LIME_DYE:Material.GRAY_DYE);ItemMeta m=item.getItemMeta();m.setDisplayName((enabled?"§a":"§c")+p.getName());
            List<String> lore=new ArrayList<>();lore.add("§7Статус: "+(enabled?"§aВключён":"§cВыключен"));lore.add("§7Версия: §f"+p.getDescription().getVersion());lore.add("§7Главный класс: §f"+p.getDescription().getMain());
            if(!p.getDescription().getDepend().isEmpty())lore.add("§7Зависимости: §f"+String.join(", ",p.getDescription().getDepend()));
            if(!p.getDescription().getSoftDepend().isEmpty())lore.add("§7Необязательные: §f"+String.join(", ",p.getDescription().getSoftDepend()));
            lore.add("§8Серверный ping: §f"+viewer.getPing()+" ms");m.setLore(lore);item.setItemMeta(m);inv.setItem(slot++,item);
        }
        inv.setItem(49,info(Material.NETHER_STAR,"§dСостояние сервера",List.of("§7Онлайн: §f"+Bukkit.getOnlinePlayers().size()+"/"+Bukkit.getMaxPlayers(),"§7TPS: §f"+new DecimalFormat("0.00").format(Bukkit.getTPS()[0]),"§7Память: §f"+((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024/1024)+" MB","§7Плагины: §f"+plugins.size(),"§7Страница: §f"+(page+1))));
        if(page>0)inv.setItem(45,info(Material.ARROW,"§eПредыдущая страница",List.of()));if((page+1)*45<plugins.size())inv.setItem(53,info(Material.ARROW,"§eСледующая страница",List.of()));
        viewer.openInventory(inv);
    }
    public void openPlayers(Player viewer){openPlayers(viewer,0);}
    private void openPlayers(Player viewer,int page){
        List<Player> players=new ArrayList<>(Bukkit.getOnlinePlayers());players.sort(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER));
        Inventory inv=create(View.PLAYERS,null,page,color(plugin.getConfig().getString("panel.playerlist-title","§8WeedSecurity §7| §dИгроки")));
        int slot=0;for(Player target:players.stream().skip((long)page*45).limit(45).toList()){if(slot>=45)break;ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(target);meta.setDisplayName("§d"+target.getName());
            String ip=target.getAddress()==null?"неизвестен":target.getAddress().getAddress().getHostAddress();
            meta.setLore(List.of("§7Баланс: §f"+balance(target),"§7Пинг: §f"+target.getPing()+" ms","§7IP: §f"+ip,"§7PIN: "+(pins.hasPin(target.getUniqueId())?"§aУстановлен":"§cНе установлен"),"","§eНажмите, чтобы открыть последние действия"));head.setItemMeta(meta);inv.setItem(slot++,head);
        }
        inv.setItem(49,info(Material.BOOK,"§dИнформация",List.of("§7Игроков онлайн: §f"+players.size(),"§7Страница: §f"+(page+1),"§8PIN отображается только как статус.")));
        if(page>0)inv.setItem(45,info(Material.ARROW,"§eПредыдущая страница",List.of()));if((page+1)*45<players.size())inv.setItem(53,info(Material.ARROW,"§eСледующая страница",List.of()));
        viewer.openInventory(inv);
    }
    private String balance(Player player){
        try{Class<?> economy=Class.forName("net.milkbowl.vault.economy.Economy");Object provider=Bukkit.getServicesManager().load(economy);if(provider==null)return "§8Недоступен";Method method=economy.getMethod("getBalance",OfflinePlayer.class);Object value=method.invoke(provider,player);return "§f"+new DecimalFormat("#,##0.00").format(((Number)value).doubleValue());}
        catch(Throwable ignored){return "§8Vault не найден";}
    }
    private Inventory create(View view,UUID target,int page,String title){Holder holder=new Holder(view,target,page);Inventory inventory=Bukkit.createInventory(holder,54,title);holder.inventory=inventory;return inventory;}
    private ItemStack info(Material material,String name,List<String> lore){ItemStack item=new ItemStack(material);ItemMeta m=item.getItemMeta();m.setDisplayName(name);m.setLore(lore);item.setItemMeta(m);return item;}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private void openActivity(Player viewer,UUID target){OfflinePlayer op=Bukkit.getOfflinePlayer(target);String name=op.getName()==null?target.toString():op.getName();String title=plugin.getConfig().getString("panel.activity-title","§8Действия §7| §d{player}").replace("{player}",name);
        Inventory inv=create(View.ACTIVITY,target,0,color(title));List<String> lines=db.recentActivity(target,45);int slot=0;
        for(String line:lines){ItemStack paper=new ItemStack(Material.PAPER);ItemMeta meta=paper.getItemMeta();meta.setDisplayName("§f"+line.substring(0,Math.min(48,line.length())));meta.setLore(List.of(line));paper.setItemMeta(meta);inv.setItem(slot++,paper);}
        if(lines.isEmpty())inv.setItem(22,info(Material.BARRIER,"§cИстория пуста",List.of("§7Для игрока пока нет записанных действий.")));
        viewer.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true);if(event.getClickedInventory()==null||event.getClickedInventory()!=event.getView().getTopInventory())return;
        Player viewer=(Player)event.getWhoClicked();
        if(event.getSlot()==45&&holder.page>0){if(holder.view==View.PLAYERS)openPlayers(viewer,holder.page-1);else if(holder.view==View.PLUGINS)openPlugins(viewer,holder.page-1);return;}
        if(event.getSlot()==53&&event.getCurrentItem()!=null){if(holder.view==View.PLAYERS)openPlayers(viewer,holder.page+1);else if(holder.view==View.PLUGINS)openPlugins(viewer,holder.page+1);return;}
        if(holder.view==View.PLAYERS&&event.getCurrentItem()!=null&&event.getCurrentItem().getType()==Material.PLAYER_HEAD){
            ItemMeta meta=event.getCurrentItem().getItemMeta();if(meta instanceof SkullMeta skull&&skull.getOwningPlayer()!=null)openActivity(viewer,skull.getOwningPlayer().getUniqueId());
        }
    }
}
