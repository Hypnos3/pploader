/*
Copyright 2014 Lisovik Denis ckyberlis@gmail.com
This file is part of PPLoader.
PPLoader is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

PPLoader is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PPLoader.  If not, see <http://www.gnu.org/licenses/>
*/
package org.cyberlis.pyloader;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;
import org.cyberlis.plugin.BukkitPluginManagerWrapper;
import org.cyberlis.plugin.PaperPluginManagerWrapper;
import org.cyberlis.plugin.PluginManagerWrapper;
import org.python.jline.internal.Log;

/**
 * Java plugin to initialize python plugin loader and provide it with a little moral boost.
 *
 */
public class PythonLoader extends JavaPlugin {

    protected PluginManagerWrapper pmw;
    public void onDisable() {}
   
    public void onEnable() {
        getServer().getLogger().info("[PPLoader] Enable plugins");

        for (Plugin p : pmw.getPlugins()) {
            if (p instanceof PythonPlugin && !p.isEnabled()) {
                pmw.enablePlugin(p);
            }
        }
    }

    /**
     * Initialize and load up the plugin loader.
     */
    @Override
    public void onLoad() {
        getServer().getLogger().info("[PPLoader] Start plugin loader");
        try {
            getServer().getLogger().info("[PPLoader] loaded from "
                    + new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        } catch (URISyntaxException e) {
            getServer().getLogger().warning("[PPLoader] error " + e.getMessage());
        }
        //check if jython.jar exists if not try to download
        // !new File("lib/jython.jar").exists() && !new File("../lib/jython.jar").exists()
        if (!new File("lib/jython.jar").exists() && !new File("../lib/jython.jar").exists()) {
            getServer().getLogger().severe("[PPLoader] Could not find lib/jython.jar!");
            return;
        }

        try {
            Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            pmw = new PaperPluginManagerWrapper();
            Log.info("Paper detected, using PaperPluginManagerImpl");
        } catch (ClassNotFoundException e) {
            pmw = new BukkitPluginManagerWrapper();
            Log.info("Paper not detected, using BukkitPluginManager");
        }

        // https://github.com/Test-Account666/PlugManX/blob/4a73d6307585f2803abf4571e409c6f0fd957857/src/main/java/com/rylinaux/plugman/pluginmanager/PaperPluginManager.java
        boolean needsload = true;

        String errorstr = "cannot ensure that the python loader class is not loaded twice!";
        Map<Pattern, PluginLoader> fileAssociations = pmw.getFileAssociations(errorstr);

        if (fileAssociations != null) {
            PluginLoader loader = fileAssociations.get(PythonPluginLoader.fileFilters[0]);
            if (loader != null) { // already loaded
                needsload = false;
            }
        }

        if (needsload) {
            pmw.registerInterface(PythonPluginLoader.class);
            File directory = this.getFile().getParentFile();
            if (directory.getName().startsWith(".")) {
                directory = directory.getParentFile();
            }
            AtomicInteger foundPy = new AtomicInteger(0);
            Consumer<File> process = (file) -> {
                try {
                    getServer().getLogger().info("[PPLoader] found Python plugin \"" + file.getName() + "\" - try to load bukkit/spigot plugin");
                    Plugin plugin = pmw.loadPlugin(file);
                    /* if (plugin != null) {
                        pmw.enablePlugin(plugin);
                    }*/
                    foundPy.incrementAndGet();
                } catch (UnknownDependencyException e) {
                    e.printStackTrace();
                }
            };
            loadDir(this.getFile().getParentFile(), process);
            if (foundPy.get() == 0) {
                loadDir(this.getFile().getParentFile().getParentFile(), process);
            }
            if (pmw != null && pmw.getPlugins() != null) {
                getServer().getLogger().info("[PPLoader] " + pmw.getPlugins().length + " plugins, where " + foundPy.get() + " are Python Plugins");
            }
        }
    }

    private void loadDir(File directory, Consumer<File> process) {
        if (directory == null) {
            return;
        }
        getServer().getLogger()
                .info("[PPLoader] searching for Python Plugins in \"" + directory.getAbsolutePath() + "/...\"");
        for (File file : directory.listFiles()) {
            for (Pattern filter : PythonPluginLoader.fileFilters) {
                Matcher match = filter.matcher(file.getName());
                if (match.find()) {
                    process.accept(file);
                }
            }
        }
    }

    public final boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pploader")) {
            if (args.length < 1) {
                return false;
            }
            String action = args[0];

            if ((!action.equalsIgnoreCase("load")) && (!action.equalsIgnoreCase("unload")) && (!action.equalsIgnoreCase("reload"))) {
                getServer().getLogger().severe("Invalid action specified.");
                return false;
            }

            if(!(sender instanceof ConsoleCommandSender) && !(sender instanceof Player && sender.isOp())){
                getServer().getLogger().severe("You do not have the permission to do this.");
                return true;
            }

            if (args.length == 1) {
                getServer().getLogger().severe("You must specify plugin name or filename");
                return true;
            }

            try {
                if (action.equalsIgnoreCase("unload")) {
                    String plName = args[1];
                    unloadPlugin(plName);
                } else if (action.equalsIgnoreCase("load")) {
                    String fileName = args[1];
                    loadPlugin(fileName);
                } else if (action.equalsIgnoreCase("reload") && args.length == 3) {
                    String plName = args[1];
                    String fileName = args[2];
                    reloadPlugin(plName, fileName);
                } else {
                    getServer().getLogger().severe("You must specify plugin name or filename");
                }
            } catch (Exception e) {
                e.printStackTrace();
                getServer().getLogger().severe("Exception while perfoming action "+action+" "+e.getMessage());
            }
            return true;
        }
        return false;
    }

    private boolean unloadPlugin(String pluginName)
      throws Exception {
        PluginManager manager = getServer().getPluginManager();
        SimplePluginManager spmanager = (SimplePluginManager)manager;

        if (spmanager != null) {
            Field pluginsField = spmanager.getClass().getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            List plugins = (List)pluginsField.get(spmanager);

            Field lookupNamesField = spmanager.getClass().getDeclaredField("lookupNames");
            lookupNamesField.setAccessible(true);
            Map lookupNames = (Map)lookupNamesField.get(spmanager);

            Field commandMapField = spmanager.getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap)commandMapField.get(spmanager);

            Field knownCommandsField = null;
            Map knownCommands = null;

            if (commandMap != null) {
                knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                knownCommands = (Map)knownCommandsField.get(commandMap);
            }
            Iterator it;
            for (Plugin plugin: manager.getPlugins()) {
                if (plugin.getDescription().getName().equalsIgnoreCase(pluginName)) {
                    manager.disablePlugin(plugin);

                    if ((plugins != null) && (plugins.contains(plugin))) {
                        plugins.remove(plugin);
                    }

                    if ((lookupNames != null) && (lookupNames.containsKey(pluginName))) {
                        lookupNames.remove(pluginName);
                    }

                    if (commandMap != null) {
                        for (it = knownCommands.entrySet().iterator(); it.hasNext();) {
                            Map.Entry entry = (Map.Entry)it.next();

                            if ((entry.getValue() instanceof PluginCommand)) {
                                PluginCommand command = (PluginCommand)entry.getValue();

                                if (command.getPlugin() == plugin) {
                                    command.unregister(commandMap);
                                    it.remove();
                                }
                            }
                        }
                    }
                }
            }
        } else {
            getServer().getLogger().warning(pluginName + " is already unloaded.");
            return true;
        }
        getServer().getLogger().info("Unloaded " + pluginName + " successfully!");
        return true;
    }

    private boolean loadPlugin(String pluginName)
    {
      try {
          File file = new File("plugins", pluginName);
          for (Pattern filter : PythonPluginLoader.fileFilters) {
              Matcher match = filter.matcher(file.getName());
              if (match.find()) {
                  try {
                      Plugin plugin = pmw.loadPlugin(file);
                      pmw.enablePlugin(plugin);
                  } catch (UnknownDependencyException e) {
                      e.printStackTrace();
                  }
              }
          }

      } catch (Exception e) {
          e.printStackTrace();
          getServer().getLogger().severe("Error loading " + pluginName + ", this plugin must be reloaded by restarting the server.");
          return false;
      }

      getServer().getLogger().info("Loaded " + pluginName + " successfully!");
      return true;
    }

    private boolean reloadPlugin(String pluginName, String fileName)
      throws Exception
    {
      boolean unload = unloadPlugin(pluginName);
      boolean load = loadPlugin(fileName);

      if ((unload) && (load)) {
          getServer().getLogger().info("Reloaded " + pluginName + " successfully!");
      } else {
          getServer().getLogger().severe("Error reloading " + pluginName + ".");

        return false;
      }

      return true;
    }

}
