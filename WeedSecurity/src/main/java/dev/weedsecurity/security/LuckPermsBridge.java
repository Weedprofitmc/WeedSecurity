package dev.weedsecurity.security;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Optional LuckPerms integration. This class deliberately has no compile-time
 * references to LuckPerms classes, so WeedSecurity can load without LuckPerms.
 */
public final class LuckPermsBridge {
    private final Object luckPerms;
    private final ClassLoader luckPermsClassLoader;

    private LuckPermsBridge(Object luckPerms, ClassLoader luckPermsClassLoader) {
        this.luckPerms = luckPerms;
        this.luckPermsClassLoader = luckPermsClassLoader;
    }

    public static LuckPermsBridge find() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> apiClass = loader.loadClass("net.luckperms.api.LuckPerms");
            Object provider = Bukkit.getServicesManager().load(apiClass);
            return provider == null ? null : new LuckPermsBridge(provider, loader);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return null;
        }
    }

    public List<String> getLoadedGroupNames() {
        try {
            Object manager = invoke(luckPerms, "getGroupManager");
            Object loaded = invoke(manager, "getLoadedGroups");
            List<String> names = new ArrayList<>();
            if (loaded instanceof Iterable<?> groups) {
                for (Object group : groups) {
                    Object name = invoke(group, "getName");
                    if (name instanceof String value) names.add(value);
                }
            }
            return names;
        } catch (ReflectiveOperationException ex) {
            return List.of();
        }
    }

    public CompletableFuture<Void> updateGroup(UUID playerId, String groupName, boolean add) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Object groupManager = invoke(luckPerms, "getGroupManager");
            Object group = invoke(groupManager, "getGroup", groupName);
            if (group == null) {
                result.completeExceptionally(new IllegalArgumentException("LuckPerms group not found: " + groupName));
                return result;
            }

            Object userManager = invoke(luckPerms, "getUserManager");
            Object loadedUser = invoke(userManager, "loadUser", playerId);
            if (!(loadedUser instanceof CompletableFuture<?> loadFuture)) {
                result.completeExceptionally(new IllegalStateException("LuckPerms loadUser did not return a future"));
                return result;
            }

            loadFuture.whenComplete((user, loadError) -> {
                if (loadError != null) {
                    result.completeExceptionally(loadError);
                    return;
                }
                try {
                    Object userData = invoke(user, "data");
                    Class<?> nodeClass = luckPermsClassLoader.loadClass("net.luckperms.api.node.Node");
                    Object builder = invokeStatic(nodeClass, "builder", "group." + invoke(group, "getName"));
                    Object node = invoke(builder, "build");
                    invoke(userData, add ? "add" : "remove", node);

                    Object saveResult = invoke(userManager, "saveUser", user);
                    if (!(saveResult instanceof CompletableFuture<?> saveFuture)) {
                        result.completeExceptionally(new IllegalStateException("LuckPerms saveUser did not return a future"));
                        return;
                    }
                    saveFuture.whenComplete((ignored, saveError) -> {
                        if (saveError != null) result.completeExceptionally(saveError);
                        else result.complete(null);
                    });
                } catch (Throwable ex) {
                    result.completeExceptionally(unwrap(ex));
                }
            });
        } catch (Throwable ex) {
            result.completeExceptionally(unwrap(ex));
        }
        return result;
    }

    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, args);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ReflectiveOperationException(cause);
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(type, name, args);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ReflectiveOperationException(cause);
        }
    }

    private static Method findMethod(Class<?> type, String name, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) {
                if (args[i] != null && !wrap(parameters[i]).isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return invocation.getCause();
        }
        return error;
    }
}
