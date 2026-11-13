package dev.weedsecurity.command;

import dev.weedsecurity.WeedSecurity;
import dev.weedsecurity.db.SecurityDatabase;
import dev.weedsecurity.security.PinService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class PinCommand implements TabExecutor {
    private static final String PREFIX="§8[§dWEEDSECURITY§8] §7";
    private final WeedSecurity plugin; private final PinService pins; private final SecurityDatabase db;
    public PinCommand(WeedSecurity plugin,PinService pins,SecurityDatabase db){this.plugin=plugin;this.pins=pins;this.db=db;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage(PREFIX+"§cЭта команда доступна только игроку.");return true;}
        UUID id=player.getUniqueId();
        if(args.length==2&&args[0].equalsIgnoreCase("set")){
            if(pins.hasPin(id)){player.sendMessage(PREFIX+"§cPIN уже установлен. Попросите оператора сбросить его.");return true;}
            if(!pins.setup(id,args[1])){player.sendMessage(PREFIX+"§cPIN должен содержать только цифры и иметь допустимую длину.");return true;}
            String ip=player.getAddress()==null?"":player.getAddress().getAddress().getHostAddress();
            pins.verify(id,args[1],ip);db.audit(id,id,"PIN_CREATED","Player created PIN");
            player.sendMessage(PREFIX+"§aPIN установлен. Защита аккаунта активирована.");return true;
        }
        if(args.length==1&&pins.hasPin(id)){
            if(pins.locked(id)){player.sendMessage(PREFIX+"§cВход временно заблокирован из-за неверных попыток.");return true;}
            String ip=player.getAddress()==null?"":player.getAddress().getAddress().getHostAddress();
            if(pins.verify(id,args[0],ip))player.sendMessage(PREFIX+"§aПроверка пройдена. Добро пожаловать!");
            else player.sendMessage(PREFIX+"§cНеверный PIN-код.");
            return true;
        }
        player.sendMessage(PREFIX+"§f/pincode set <PIN> §8— §7первичная установка");
        player.sendMessage(PREFIX+"§f/pincode <PIN> §8— §7подтверждение входа");return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!(sender instanceof Player player))return Collections.emptyList();
        if(args.length==1&&!pins.hasPin(player.getUniqueId()))return filter(List.of("set"),args[0]);
        // Never suggest PIN values or previously entered credentials.
        return Collections.emptyList();
    }
    private List<String> filter(List<String> values,String prefix){return values.stream().filter(v->v.regionMatches(true,0,prefix,0,prefix.length())).toList();}
}
